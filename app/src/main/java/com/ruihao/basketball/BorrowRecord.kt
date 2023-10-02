package com.ruihao.basketball

import java.time.LocalDateTime

data class BorrowRecord(
    val id: String,
    val borrowerId: String,
    val createdTime: String,    //ISO_LOCAL_DATE_TIME format, eg: 2022-01-06T21:30:10
    val type: Int,              //0: borrow, 1: return
    val captureImagePath: String,
) : java.io.Serializable