//
// Created by apple on 9/10/23.
//
#include "modbus_wrapper.h"

static modbus_t *g_ptrModbusCtx = NULL;

bool do_open_modbus()
{
    if (g_ptrModbusCtx != nullptr) {
        LOGD("Modbus already connected!");
        return true;
    }

    g_ptrModbusCtx = modbus_new_rtu("/dev/ttyS9", 9600,
                                    'N', 8, 1);
    if (g_ptrModbusCtx == NULL) {
        LOGE("Failed to create a modbus rtu instance: %s", modbus_strerror(errno));
        return false;
    }

    if (modbus_rtu_set_serial_mode(g_ptrModbusCtx, MODBUS_RTU_RS232) == -1) {
        LOGE("Failed to set serial mode to RS232: %s", modbus_strerror(errno));
        g_ptrModbusCtx = NULL;
        return false;
    }

    if (modbus_connect(g_ptrModbusCtx) == -1) {
        LOGE("Failed to connect to modbus server: %s", modbus_strerror(errno));
        modbus_free(g_ptrModbusCtx);
        g_ptrModbusCtx = NULL;
        return false;
    }

    if (modbus_set_slave(g_ptrModbusCtx, 1) == -1 ) {
        LOGE("Failed to connect to modbus server: %s", modbus_strerror(errno));
        modbus_close(g_ptrModbusCtx);
        modbus_free(g_ptrModbusCtx);
        g_ptrModbusCtx = NULL;
        return false;
    }

    return true;
}

bool do_close_modbus()
{
    if (g_ptrModbusCtx == NULL) {
        LOGD("Modbus already closed!");
        return true;
    }

    if (modbus_flush(g_ptrModbusCtx) == -1) {
        LOGE("Failed to flush modbus: %s", modbus_strerror(errno));
        return false;
    }
    modbus_close(g_ptrModbusCtx);
    modbus_free(g_ptrModbusCtx);
    g_ptrModbusCtx = NULL;

    return true;
}

bool do_restart_modbus()
{
    do_close_modbus();
    do_open_modbus();
    return true;
}

bool do_write_modbus_bit(int address, int value)
{
    if (g_ptrModbusCtx == NULL) {
        LOGE("Cannot write modbus bit, modbus not connected!");
        return false;
    }

    int bRet = modbus_write_bit(g_ptrModbusCtx, address, value);
    if (bRet == -1) {
        LOGE("Failed to write bit to modbus: %s", modbus_strerror(errno));
        return false;
    }

    LOGD("Write modbus succeed: address=%d, value=%d", address, value);
    return true;
}

bool do_write_modbus_register(int address, int value)
{
    if (g_ptrModbusCtx == NULL) {
        return false;
    }

    int bRet = modbus_write_register(g_ptrModbusCtx, address, value);
    if (bRet == -1) {
        LOGE("Failed to write register to modbus: %s", modbus_strerror(errno));
        return false;
    }

    return true;
}

int do_read_modbus_bit(int address)
{
    if (g_ptrModbusCtx == NULL) {
        return -1;
    }
    uint8_t dest[1];

    int nRet = modbus_read_bits(g_ptrModbusCtx, address, 1, dest);
    if (nRet == -1) {
        LOGE("Failed to read bit at address %d: %s", address, modbus_strerror(errno));
        return nRet;
    }

    return dest[0];
}

int do_read_modbus_register(int address)
{
    if (g_ptrModbusCtx == NULL) {
        return -1;
    }
    uint16_t dest[1];

    int nRet = modbus_read_registers(g_ptrModbusCtx, address, 1, dest);
    if (nRet == -1) {
        LOGE("Failed to read holding register at address %d: %s", address,
             modbus_strerror(errno));
        return nRet;
    }

    return dest[0];
}