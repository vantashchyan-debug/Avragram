load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")

# Avragram core: pure Foundation code that low-level Telegram modules may use (no UI, no Telegram deps).
swift_library(
    name = "AvraCore",
    module_name = "AvraCore",
    srcs = glob([
        "Sources/**/*.swift",
    ]),
    visibility = [
        "//visibility:public",
    ],
)
