// swift-tools-version: 6.4
import PackageDescription

let swiftSettings: [SwiftSetting] = [
    .swiftLanguageMode(.v6),
    .enableUpcomingFeature("MemberImportVisibility"),
    .enableUpcomingFeature("NonisolatedNonsendingByDefault"),
    .enableUpcomingFeature("InferIsolatedConformances"),
    .enableUpcomingFeature("ExistentialAny"),
    .enableUpcomingFeature("ImmutableWeakCaptures"),
]

let package = Package(
    name: "KodosiGhostty",
    platforms: [
        .macOS("26.0"),
        .iOS("26.0"),
    ],
    products: [
        .library(name: "GhosttyTerminal", targets: ["GhosttyTerminal"]),
    ],
    targets: [
        .target(
            name: "GhosttyKit",
            dependencies: ["libghostty"],
            path: "Sources/GhosttyKit",
            linkerSettings: [
                .linkedLibrary("c++"),
                .linkedFramework("Carbon", .when(platforms: [.macOS])),
                .linkedFramework("CoreFoundation", .when(platforms: [.iOS])),
                .linkedFramework("CoreGraphics", .when(platforms: [.iOS])),
                .linkedFramework("CoreText", .when(platforms: [.iOS])),
                .linkedFramework("IOSurface", .when(platforms: [.iOS])),
                .linkedFramework("Metal", .when(platforms: [.iOS])),
                .linkedFramework("QuartzCore", .when(platforms: [.iOS])),
            ]
        ),
        .target(
            name: "GhosttyTerminal",
            dependencies: ["GhosttyKit"],
            path: "Sources/GhosttyTerminal",
            swiftSettings: swiftSettings
        ),
        .binaryTarget(
            name: "libghostty",
            path: "Vendor/GhosttyKit.xcframework"
        ),
        .testTarget(
            name: "GhosttyKitTest",
            dependencies: ["GhosttyKit", "GhosttyTerminal"],
            resources: [
                .copy("Resources/visual-checkpoint.snapshot"),
                .copy("Resources/visible-image-checkpoint.snapshot"),
                .copy("Resources/mouse-capture-checkpoint.snapshot"),
                .copy("Resources/mouse-release-checkpoint.snapshot"),
            ],
            swiftSettings: swiftSettings
        ),
    ]
)
