require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "rn-telegram-login"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/your-org/rn-telegram-login"
  s.license      = "MIT"
  s.authors      = { "Author" => "author@example.com" }

  s.platforms    = { :ios => "15.0" }

  s.source       = { :git => "https://github.com/your-org/rn-telegram-login.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{swift,m,h}"

  s.swift_version = "5.7"

  s.dependency "React-Core"
end
