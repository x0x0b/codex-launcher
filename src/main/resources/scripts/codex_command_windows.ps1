$ErrorActionPreference = "Stop"
try {
%s
}
finally {
  Remove-Item -LiteralPath -Force "$PSCommandPath"
}
