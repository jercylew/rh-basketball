//
// Created by apple on 9/10/23.
//
#include <jni.h>
#include <string>
#include <cerrno>
#include <android/log.h>
#include "modbus/modbus.h"

#ifndef BASKETBALL_MODBUS_WRAPPER_H
#define BASKETBALL_MODBUS_WRAPPER_H

#define TAG     "RH_BASKETBALL_SerialPort"

#define LOGI(fmt, args...) __android_log_print(ANDROID_LOG_INFO,  TAG, fmt, ##args)
#define LOGD(fmt, args...) __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt, ##args)
#define LOGE(fmt, args...) __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)

bool do_open_modbus();
bool do_close_modbus();
bool do_restart_modbus();
bool do_write_modbus_bit(int address, int value);
bool do_write_modbus_register(int address, int value);
int do_read_modbus_bit(int address);
int do_read_modbus_register(int address);


#endif //BASKETBALL_MODBUS_WRAPPER_H
