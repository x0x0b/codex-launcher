$ErrorActionPreference = "Stop"
try {
%s
}
finally {
  Remove-Item -LiteralPath "$PSCommandPath" -Force
}
