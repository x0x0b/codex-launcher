$ErrorActionPreference = "Stop"
try {
%s
}
finally {
  Remove-Item -Force "$PSCommandPath"
}
