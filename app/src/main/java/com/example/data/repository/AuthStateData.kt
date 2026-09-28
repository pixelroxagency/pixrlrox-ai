package com.example.data.repository

enum class AuthStateData {
    SignedOut,
    SigningIn,
    SignedIn,
    Refreshing,
    Expired,
    Error
}
