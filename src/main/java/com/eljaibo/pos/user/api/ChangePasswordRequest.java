package com.eljaibo.pos.user.api;

public record ChangePasswordRequest(@ValidPassword String currentPassword,
                                    @ValidPassword String newPassword) { }
