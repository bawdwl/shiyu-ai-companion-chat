# Room, WorkManager, OkHttp and Compose publish their own consumer rules.
# Keep this file for project-specific release rules only.

# Compile-time annotations referenced by Tink are not runtime dependencies.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
