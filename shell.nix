let
  pkgs = import <nixpkgs> {
    config = {
      allowUnfree = true;
      android_sdk.accept_license = true;
    };
  };
  sdk = pkgs.androidenv.composeAndroidPackages {
    platformVersions = [ "34" ];
    buildToolsVersions = [ "34.0.0" ];
    includeEmulator = false;
    includeSystemImages = false;
  };
in
pkgs.mkShell {
  packages = [ pkgs.jdk17 ];
  ANDROID_HOME = "${sdk.androidsdk}/libexec/android-sdk";
  JAVA_HOME = pkgs.jdk17.home;
}
