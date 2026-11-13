param(
  [Parameter(Mandatory = $true)][ValidateSet('move','click','doubleclick','type','key','shot','size')][string]$Action,
  [int]$X = 0, [int]$Y = 0,
  [string]$Text = '',
  [string]$Out = ''
)
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32Mouse {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, int e);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, int e);
  public const uint LEFTDOWN = 0x0002, LEFTUP = 0x0004;
}
"@
switch ($Action) {
  'size'    { $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds; "$($b.Width)x$($b.Height)" }
  'move'    { [Win32Mouse]::SetCursorPos($X, $Y) | Out-Null; "moved $X,$Y" }
  'click'   {
    [Win32Mouse]::SetCursorPos($X, $Y) | Out-Null
    Start-Sleep -Milliseconds 120
    [Win32Mouse]::mouse_event([Win32Mouse]::LEFTDOWN, 0, 0, 0, 0)
    Start-Sleep -Milliseconds 60
    [Win32Mouse]::mouse_event([Win32Mouse]::LEFTUP, 0, 0, 0, 0)
    "clicked $X,$Y"
  }
  'doubleclick' {
    [Win32Mouse]::SetCursorPos($X, $Y) | Out-Null
    1..2 | ForEach-Object {
      [Win32Mouse]::mouse_event([Win32Mouse]::LEFTDOWN, 0, 0, 0, 0)
      Start-Sleep -Milliseconds 40
      [Win32Mouse]::mouse_event([Win32Mouse]::LEFTUP, 0, 0, 0, 0)
      Start-Sleep -Milliseconds 90
    }
    "double clicked $X,$Y"
  }
  'type'    { [System.Windows.Forms.SendKeys]::SendWait($Text); "typed $($Text.Length) chars" }
  'key'     { [System.Windows.Forms.SendKeys]::SendWait($Text); "sent $Text" }
  'shot'    {
    if (-not $Out) { throw 'shot 需要 -Out <png 路径>' }
    $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($b.X, $b.Y, 0, 0, $bmp.Size)
    $bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    "saved $Out ($($b.Width)x$($b.Height))"
  }
}
