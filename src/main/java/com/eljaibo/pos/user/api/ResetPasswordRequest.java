package com.eljaibo.pos.user.api;

public record ResetPasswordRequest(@ValidPassword String newPassword) { }
