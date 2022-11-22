# Documentation: https://docs.brew.sh/Formula-Cookbook
#                https://rubydoc.brew.sh/Formula

# Named Autopsy4 to avoid conflict with the autopsy formula in repos
# A package installer can be generated using brew-pkg: https://github.com/timsutton/brew-pkg
# Can be run locally with `brew install --debug --build-from-source --verbose <path_to_this_file>`
class Autopsy4 < Formula
  AUTOPSY_RESOURCE_URL = "https://github.com/sleuthkit/autopsy/releases/download/autopsy-4.19.2/autopsy-4.19.2.zip".freeze
  AUTOPSY_RESOURCE_SHA256 = "b1ca770df47f09512276fee16c184644cdd9a2591edfdb622a3177896f299893".freeze
  TSK_RESOURCE_URL = "https://github.com/sleuthkit/sleuthkit/releases/download/sleuthkit-4.11.1/sleuthkit-4.11.1.tar.gz".freeze
  TSK_RESOURCE_SHA256 = "8ad94f5a69b7cd1a401afd882ab6b8e5daadb39dd2a6a3bbd5aecee2a2ea57a0".freeze

  desc "Autopsy® is a digital forensics platform and graphical interface to The Sleuth Kit® and other digital forensics tools. It can be used by law enforcement, military, and corporate examiners to investigate what happened on a computer. You can even use it to recover photos from your camera's memory card. "
  homepage "http://www.sleuthkit.org/autopsy/"

  url AUTOPSY_RESOURCE_URL
  sha256 AUTOPSY_RESOURCE_SHA256
  license "Apache-2.0"

  depends_on "afflib"
  depends_on "libewf"

  depends_on "testdisk"

  depends_on "libheif"

  depends_on "gst-libav"
  depends_on "gst-plugins-bad"
  depends_on "gst-plugins-base"
  depends_on "gst-plugins-good"
  depends_on "gst-plugins-ugly"
  depends_on "gstreamer"

  depends_on "libtool" => :build
  depends_on "autoconf" => :build
  depends_on "automake" => :build
  depends_on "zip" => :build
  depends_on "gnu-tar" => :build
  depends_on "ant" => :build

  resource "sleuthkit" do
    url TSK_RESOURCE_URL
    sha256 TSK_RESOURCE_SHA256
  end

  # sha256 calculated using curl <url> | sha256sum
  # TODO could create separate for build and run
  on_linux do
    depends_on "sqlite"

    on_intel do
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jdk8u345+1-linux-amd64-full.tar.gz"
        sha256 "70899945312ee630190b8c4f9dc1124af91fec14987e890074bda40126ec186e"
      end
    end
  end
  on_macos do
    uses_from_macos "sqlite"

    on_arm do 
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jdk8u345+1-macos-aarch64-full.tar.gz"
        sha256 "d5d6fe21ece5d6c29bdf96ba1dada4ba1f71569eb5233be3434c2aa7a4aeb3e7"
      end
    end
    on_intel do
      resource "liberica_jvm" do
        url "https://download.bell-sw.com/java/8u345+1/bellsoft-jdk8u345+1-macos-amd64-full.tar.gz"
        sha256 "426fd1f299a31d3895b5d38fb24040d599faa36886528ec10863b2cd470bb4b6"
      end
    end
  end

  conflicts_with "ffind", because: "both install a `ffind` executable"
  conflicts_with "sleuthkit", because: "both install sleuthkit items"
  conflicts_with "autopsy", because: "both install sleuthkit items and have autopsy executables"

  def install
    ENV.deparallelize
    install_dir = File.join(prefix, "install")

    # ----- SETUP JVM -----
    java_home_path = File.join(install_dir, "liberica_jvm")
    system "mkdir", "-p", java_home_path
    resource("liberica_jvm").stage(java_home_path)
    ENV["JAVA_HOME"] = java_home_path
    ENV["PATH"] = "#{java_home_path}/bin:#{ENV["PATH"]}"
    ENV["ANT_FOUND"] = Formula["ant"].opt_bin/"ant"
    
    # ----- SETUP TSK -----
    sleuthkit_bin_path = File.join(install_dir, "sleuthkit")
    system "mkdir", "-p", sleuthkit_bin_path
    resource("sleuthkit").stage(sleuthkit_bin_path)
    cd sleuthkit_bin_path do
        system "./configure", "--disable-dependency-tracking", "--prefix=#{prefix}"
        system "make"
        system "make", "install"
    end
    
    # ----- RUN UNIX SETUP SCRIPT -----
    autopsy_tmp_path = `find $(pwd) -maxdepth 1 -type d -name "autopsy-*.*.*"`.strip()
    autopsy_install_path = File.join(install_dir, "autopsy")
    system "cp", "-a", "#{autopsy_tmp_path}/.", autopsy_install_path
    
    unix_setup_script = File.join(autopsy_install_path, "unix_setup.sh")

    # TODO remove for the future
    system "rm", unix_setup_script
    system "curl", "-o", unix_setup_script, "https://raw.githubusercontent.com/gdicristofaro/autopsy/8425_linuxMacBuild/unix_setup.sh"

    system "chmod", "a+x", unix_setup_script

    ENV["TSK_JAVA_LIB_PATH"] = File.join(prefix, "share", "java")
    system unix_setup_script, "-j", "#{java_home_path}"

    open(File.join(autopsy_install_path, "etc", "autopsy.conf"), 'a') { |f|
      # gstreamer needs the 'gst-plugin-scanner' to locate gstreamer plugins like the ones that allow gstreamer to play videos in autopsy
      # so, the jreflags allow the initial gstreamer lib to be loaded and the  'GST_PLUGIN_SYSTEM_PATH' along with 'GST_PLUGIN_SCANNER'
      # allows gstreamer to find plugin dependencies
      f.puts("export jreflags=\"-Djna.library.path=/usr/local/lib $jreflags\"") 
      f.puts("export GST_PLUGIN_SYSTEM_PATH=\"/usr/local/lib/gstreamer-1.0\"")
      f.puts("export GST_PLUGIN_SCANNER=\"#{Formula["gstreamer"].prefix}/libexec/gstreamer-1.0/gst-plugin-scanner\"")
    }

    bin_autopsy = File.join(bin, "autopsy")
    system "ln", "-s", File.join(autopsy_install_path, "bin", "autopsy"), bin_autopsy
  end

  test do
    system "#{bin}/autopsy", "--help"
  end
end
