package com.ruihao.basketball

data class User(
    val name: String,
    val id: String,
    val barQRNo: String,
    val icCardNo: String,
    val age: Int,
    val gender: String,
    val classNo: String,
    val gradeNo: String,
    val photoUrl: String,
    val isAdmin: Boolean,
) : java.io.Serializable