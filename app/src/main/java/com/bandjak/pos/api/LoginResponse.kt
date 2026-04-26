package com.bandjak.pos.api

import com.bandjak.pos.model.User

data class LoginResponse(
    val message: String,
    val user: User
)