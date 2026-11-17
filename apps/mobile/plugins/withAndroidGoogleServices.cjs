const { withProjectBuildGradle } = require("expo/config-plugins");

// The app must run on devices without Google Play Services, so the Google code it does
// not call must not reach the APK. The QR scanner reads codes with a plain Java library,
// so nothing in the build pulls Play Services or Firebase in any more.
const EXCLUDED_MODULES = [
  ["com.google.android.gms", "play-services-code-scanner"],
  ["com.google.android.gms", "play-services-mlkit-document-scanner"],
  ["com.google.android.gms", "play-services-cloud-messaging"],
  ["com.google.android.gms", "play-services-auth"],
  ["com.google.android.gms", "play-services-auth-base"],
  ["com.google.android.gms", "play-services-auth-blockstore"],
  ["com.google.android.gms", "play-services-auth-api-phone"],
  ["com.google.android.gms", "play-services-fido"],
  ["com.google.android.gms", "play-services-identity-credentials"],
  ["com.google.android.gms", "play-services-stats"],
  ["androidx.credentials", "credentials-play-services-auth"],
  ["com.google.firebase", "firebase-messaging"],
  ["com.google.firebase", "firebase-installations"],
  ["com.google.firebase", "firebase-installations-interop"],
  ["com.google.firebase", "firebase-iid-interop"],
  ["com.google.firebase", "firebase-measurement-connector"],
  ["com.google.firebase", "firebase-datatransport"],
];

// The excludes live on the root project so they cover every module's runtime classpath.
// Compilation keeps the artifacts, so a library that still references a Google type builds.
module.exports = function withAndroidGoogleServices(config) {
  return withProjectBuildGradle(config, (nextConfig) => {
    if (nextConfig.modResults.language !== "groovy") {
      throw new Error("withAndroidGoogleServices expects a Groovy android/build.gradle.");
    }
    if (nextConfig.modResults.contents.includes("exclude group: 'com.google.android.gms'")) {
      return nextConfig;
    }
    const excludes = EXCLUDED_MODULES.map(
      ([group, module]) => `    exclude group: '${group}', module: '${module}'`,
    ).join("\n");
    nextConfig.modResults.contents = nextConfig.modResults.contents.replace(
      /\nallprojects \{/,
      [
        "",
        "allprojects {",
        "  // T3 Code runs on devices without Google Play Services.",
        "  configurations.matching { it.name.endsWith('RuntimeClasspath') }.configureEach {",
        excludes,
        "  }",
        "",
      ].join("\n"),
    );
    return nextConfig;
  });
};
