# Preserve all public classes, and their public and protected fields and
# methods.

-keep public class com.solanamobile.seedvault.* {
    public protected *;
}
