require "json"
package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-geetest2"
  s.version      = package['version']
  s.summary      = package['description']
  s.author       = package['author']
  s.homepage     = package['homepage']
  s.license      = package['license']
  s.platform     = :ios, "8.0"
  s.requires_arc = true
  s.source       = { :git => "https://github.com/shimohq/react-native-geetest2.git", :tag => "v#{s.version}" }
  s.source_files = "ios/**/*.{h,m}"

  s.dependency 'React'
  s.dependency 'GeeTestSDK'

  s.static_framework      = true
end
