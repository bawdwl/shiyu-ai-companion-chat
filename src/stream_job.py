"""后台生成任务：协作停止，任务结束后才释放服务门锁。"""
import json
import logging
import re
from difflib import SequenceMatcher
from queue import Queue
from threading import Event, Lock, Thread, Timer
from characters import conversation_messages
from providers import ProviderConfigurationError, ProviderError


class GenerationStopped(RuntimeError):
    pass


REPEAT_REQUEST_CUES=('重复','再说一遍','上一条','刚才说','之前说','原话','你说过')


def _repeat_text(value):
    text=str(value or '').lower()
    text=re.sub(r'[（(][^（）()\n]{1,120}[）)]','',text)
    return re.sub(r'[\s，。！？!?；：、,.]+','',text)


def _repeat_sentences(value):
    text=re.sub(r'[（(][^（）()\n]{1,120}[）)]','',str(value or ''))
    return {
        normalized
        for sentence in re.split(r'(?<=[。！？!?；;])|\n+',text)
        if len(normalized:=_repeat_text(sentence))>=6
    }


def answer_repeats_previous(answer,messages):
    previous_answers=[
        str(message.get('content') or '')
        for message in messages[:-1]
        if message.get('role')=='assistant'
    ][-6:]
    current=_repeat_text(answer)
    current_sentences=_repeat_sentences(answer)
    if not current or not previous_answers:
        return False,0.0
    best=0.0
    best_overlap=0.0
    for previous in previous_answers:
        old=_repeat_text(previous)
        if not old:
            continue
        ratio=SequenceMatcher(None,current,old,autojunk=False).ratio()
        containment=min(len(current),len(old))/max(len(current),len(old)) if (
            current in old or old in current
        ) else 0.0
        old_sentences=_repeat_sentences(previous)
        overlap=(
            len(current_sentences & old_sentences)/len(current_sentences)
            if current_sentences and old_sentences
            else 0.0
        )
        best_overlap=max(best_overlap,overlap)
        best=max(best,ratio,containment,overlap)
    return best>=0.72 or best_overlap>=0.5,best


def retry_messages_without_previous_answer(messages):
    updated=[dict(message) for message in messages]
    replaced=0
    for index in range(len(updated)-2,-1,-1):
        if updated[index].get('role')=='assistant' and replaced<3:
            updated[index]['content']=(
                '（这条旧角色回复的原文已从本轮上下文省略，'
                '仅保留它发生过这一事实；不要复述或续写其原句。）'
            )
            replaced+=1
    return updated


class StreamJob:
    def __init__(self,engine,store,current,message,gate,timeout,max_new_tokens=256,policy_reply=None,response_source='identity_policy',
                 characters=None,prompt_additions=None,prompt_builder=None,provider_name=None,
                 model_mode=None,
                 request_id=None,record_usage=None,validate_messages=None,image_id=None,
                 buffer_until_validated=False,transform_answer=None,after_save=None,
                 after_save_details=None):
        self.engine=engine;self.store=store;self.current=current;self.message=message;self.gate=gate
        self.max_new_tokens=max_new_tokens;self.policy_reply=policy_reply;self.response_source=response_source
        self.characters=characters;self.prompt_additions=prompt_additions or []
        self.prompt_builder=prompt_builder
        self.provider_name=provider_name
        self.model_mode=model_mode or self.current.get('model_mode','base')
        self.request_id=request_id
        self.record_usage=record_usage
        self.validate_messages=validate_messages
        self.image_id=image_id
        self.buffer_until_validated=bool(buffer_until_validated)
        self.transform_answer=transform_answer
        self.after_save=after_save
        self.after_save_details=after_save_details
        self.stream_buffer=[]
        self.events=Queue();self.stop=Event();self.finished=Event();self.state_lock=Lock()
        self.reason=None;self.committed=False
        self.timer=Timer(timeout,lambda:self.cancel('timeout'))
        self.timer.daemon=True
        self.thread=Thread(target=self.run,daemon=True)

    def cancel(self,reason='stopped'):
        with self.state_lock:
            if self.finished.is_set() or self.committed:
                return False
            self.reason=self.reason or reason
            self.stop.set()
            return True

    def start(self):
        self.timer.start();self.thread.start()

    def emit(self,text):
        if text and not self.stop.is_set():
            if self.buffer_until_validated:
                self.stream_buffer.append(text)
            else:
                self.events.put(dict(event='delta',data={'text':text}))

    def run(self):
        try:
            if self.policy_reply is not None:
                answer=self.policy_reply
                metrics=dict(seconds=0.0,output_tokens=0,response_source=self.response_source)
                self.emit(answer)
            else:
                trace={}
                if self.prompt_builder:
                    messages,trace=self.prompt_builder(self.stop)
                else:
                    messages=conversation_messages(self.current,self.message,self.characters)
                if self.stop.is_set():
                    raise GenerationStopped()
                if messages and self.prompt_additions:
                    messages[0]['content'] += '\n\n' + '\n\n'.join(self.prompt_additions)
                if self.validate_messages:
                    self.validate_messages(messages)
                options={'generation_overrides':trace['generation_preset']} if trace.get('generation_preset') else {}
                repeat_guard=bool(
                    (trace.get('provider_switch_from') or trace.get('local_repeat_guard'))
                    and not any(cue in self.message for cue in REPEAT_REQUEST_CUES)
                )
                if repeat_guard:
                    # The first candidate stays private until we know it is not
                    # merely an echo of the previous model's answer.
                    self.buffer_until_validated=True
                answer,metrics=self.engine.reply_stream(messages,self.model_mode,self.stop,self.emit,
                                                       max_new_tokens=self.max_new_tokens,**options)
                if repeat_guard:
                    repeated,similarity=answer_repeats_previous(answer,messages)
                    retry_count=0
                    while repeated and retry_count<2:
                        retry_count+=1
                        self.stream_buffer.clear()
                        retry_messages=(
                            messages if retry_count==1
                            else retry_messages_without_previous_answer(messages)
                        )
                        retry_overrides=dict(options.get('generation_overrides') or {})
                        retry_overrides['repetition_penalty']=max(
                            float(retry_overrides.get('repetition_penalty',1.0)),
                            1.2 if retry_count==1 else 1.3,
                        )
                        retry_options={'generation_overrides':retry_overrides}
                        answer,metrics=self.engine.reply_stream(
                            retry_messages,
                            self.model_mode,
                            self.stop,
                            self.emit,
                            max_new_tokens=self.max_new_tokens,
                            **retry_options,
                        )
                        repeated,similarity=answer_repeats_previous(
                            answer,retry_messages,
                        )
                    metrics['provider_switch_from']=trace.get('provider_switch_from')
                    metrics['local_repeat_guard']=bool(trace.get('local_repeat_guard'))
                    metrics['repetition_retry_count']=retry_count
                    metrics['repetition_guard_similarity']=round(similarity,3)
                if self.transform_answer:
                    answer,guard_metrics=self.transform_answer(answer)
                    metrics.update(guard_metrics or {})
                if trace.get('memory_warning'):
                    metrics['memory_warning']=trace['memory_warning']
                if metrics.get('context_omitted_turns'):
                    metrics['memory_warning']='本轮仍有部分旧对话超过上下文预算，可在会话记忆中继续整理。'
            saved_answer=answer
            if self.buffer_until_validated and answer and not self.stop.is_set():
                self.events.put(dict(event='delta',data={'text':answer}))
            with self.state_lock:
                if self.stop.is_set():
                    raise GenerationStopped()
                if not saved_answer.strip():
                    raise RuntimeError('空回答')
                # 与停止请求串行确定提交点；先完成提交的请求不会再被停止撤销。
                saved=self.store.save_turn(
                    self.current['id'],self.message,saved_answer,
                    image_id=self.image_id,
                )
                self.committed=True
            if self.record_usage:
                self.record_usage(self.current['id'],self.provider_name,self.request_id,metrics)
            if self.after_save_details:
                try:
                    self.after_save_details(
                        self.current,
                        saved,
                        self.message,
                        saved_answer,
                        messages if 'messages' in locals() else [],
                        trace if 'trace' in locals() else {},
                        metrics,
                        self.response_source if self.policy_reply is not None else 'model',
                    )
                except Exception:
                    logging.exception('聊天已保存，但调试追踪写入失败')
            if self.after_save:
                try:
                    self.after_save(self.current,saved,self.message,saved_answer)
                except Exception:
                    logging.exception('聊天已保存，但自动记忆任务入队失败')
            self.events.put(dict(event='done',data=dict(answer=saved_answer,display=None,metrics=metrics,
                                session_id=self.current['id'],model_mode=self.model_mode,
                                provider=self.provider_name)))
        except Exception as exc:
            if self.stop.is_set():
                code=self.reason or 'stopped'
                detail='生成超时，未保存本轮。' if code=='timeout' else '生成已停止，未保存本轮。'
            elif isinstance(exc,ValueError):
                code='invalid_context';detail=str(exc)
            elif isinstance(exc,ProviderConfigurationError):
                code='provider_config';detail=str(exc)
            elif isinstance(exc,ProviderError):
                code='provider_error';detail=str(exc)
            else:
                logging.exception('流式生成失败')
                code='generation_failed';detail='生成失败，未保存本轮，请查看终端。'
            self.events.put(dict(event='error',data=dict(code=code,detail=detail)))
        finally:
            self.timer.cancel()
            self.finished.set()
            self.gate.release()


def encode_sse(event):
    return f"event: {event['event']}\ndata: {json.dumps(event['data'],ensure_ascii=False)}\n\n"
