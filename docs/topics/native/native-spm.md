[//]: # (title: Swift Package export setup)

Kotlin/Native output for an Apple target can be consumed as an SPM dependency.
For example, if your Kotlin Multiplatform project has an iOS target, and you want to make the binary available to iOS
developers in Xcode.

This guide shows how to do this using the [XCFramework building](multiplatform-build-native-binaries.md#build-xcframeworks)
capability of Kotlin Gradle.

## Prepare locations for the files to be uploaded

To make your framework available to be consumed, you will need to upload two files to be available for download,
a zip archive of the XCFramework itself and the `Package.swift` file.

The ZIP archive of the XCFramework should be uploaded to a convenient file storage with direct access (for example,
S3, GitHub Releases, or a Maven repository). Choose the option that is easiest to integrate into your workflow.

### Options for Package.swift distribution
The `Package.swift` that describes the Swift Package needs to be placed in a Git repository:
* Store the `Package.swift` file in an independent repository to make its versioning separate from the
  KMP project the file describes. This is the recommended approach, allowing scalability and generally easier
  maintenance.
* Put the `Package.swift` file next to your KMP code for a more straightforward approach. Keep in mind that if
  you are using Semantic Versioning tags for Kotlin, versioning the SPM package in the same repository can become
  challenging.
* Store the `Package.swift` within the consumer project's repo to avoid the versioning and maintenance issues.

  However, this approach can cause problems for multi-repository SPM setups of the consumer project and further automation:
  1. Only one consumer package in a multi-package project can depend on the external module, to avoid dependency conflicts
  within the project. So all the logic depending on your KMP module should be encapsulated in a particular consumer package.
  2. If you publish the KMP project using an automated CI process, this process would need to include publishing the
  updated `Package.swift` file to the consumer repo. Such a process can be unpredictable and difficult to maintain.

Choose the options that suit your needs best and set them up. Then move on to the next step, where you'll create the files. 

## Create the XCFramework and the package description files

> The following example shows how to provide an SPM package for a Kotlin Multiplatform project with native UI
> implementations. For projects using Compose Multiplatform, substitute `shared` in code examples for `composeApp`.
>
{type="tip"}

To be able to provide a Swift package, you need to do the following:
1. Set up publishing of an [XCFramework](multiplatform-build-native-binaries.md#build-xcframeworks). Add an `XCFramework`
call to your iOS targets description in the `shared/build.gradle.kts` file:
   ```kotlin
   import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
   
   kotlin {
      // other KMP targets
      // ...
      val xcf = XCFramework()
   
      listOf(
         iosX64(),
         iosArm64(),
         iosSimulatorArm64(),
      ).forEach {
           it.binaries.framework {
              baseName = "Shared"
              xcf.add(this)
           }
        }
      //...
   }
   ```
2. Sync the updated Gradle configuration.
3. Run the Gradle task to create the framework: `./gradlew :shared:assembleSharedReleaseXCFramework`
4. The resulting framework will be created as the `shared/build/XCFrameworks/release/shared.xcframework` file in your project directory.
5. Put the `shared.xcframework` file in a ZIP archive and upload it to a file storage with direct access.
6. Create a `Package.swift` file with the following code:
   ```Swift
   // swift-tools-version:5.3
   import PackageDescription
    
   let package = Package(
      name: "shared",
      platforms: [
        .iOS(.v14),
      ],
      products: [
         .library(name: "shared", targets: ["shared"])
      ],
      targets: [
         .binaryTarget(name: "shared", url: "<link to the uploaded XCFramework ZIP file>")
      ],
   )
   ```
7. Upload the file to the repository you settled on earlier.

Now that both files are accessible, you can try and set up the dependency:
* In an Xcode project, choose **File | Add Package Dependencies...** and provide the Git URL for the `Package.swift` file.
* In an SPM project, create a dependency following the Apple documentation on [Package.Dependency](https://developer.apple.com/documentation/packagedescription/package/dependency).