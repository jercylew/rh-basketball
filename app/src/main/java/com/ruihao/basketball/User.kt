package com.ruihao.basketball

data class User(
    val name: String,
    val id: String,
    val barQRNo: String,
    val icCardNo: String,
    val age: Int,
    val gender: String,
    val classGrade: String,
    val photoUrl: String,
    val isAdmin: Boolean,
)