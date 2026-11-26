load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")

# Avragram for iPhone: the Avragram settings hub, Fun (games) and the AvraChess / Sea battle engines.
swift_library(
    name = "AvraUI",
    module_name = "AvraUI",
    srcs = glob([
        "Sources/**/*.swift",
    ]),
    deps = [
        "//Swiftgram/SGSwiftUI:SGSwiftUI",
        "//Swiftgram/SGSettingsUI:SGSettingsUI",
        "//submodules/SSignalKit/SwiftSignalKit:SwiftSignalKit",
        "//submodules/Display:Display",
        "//submodules/TelegramCore:TelegramCore",
        "//submodules/Postbox:Postbox",
        "//submodules/TelegramPresentationData:TelegramPresentationData",
        "//submodules/AccountContext:AccountContext",
        "//submodules/TelegramUIPreferences:TelegramUIPreferences",
    ],
    visibility = [
        "//visibility:public",
    ],
)
