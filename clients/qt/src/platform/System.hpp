#pragma once

#include <QtSystemDetection>

#ifdef Q_OS_WIN
#include "platform/windows/ToastNotificationDriver.hpp"
#include "platform/windows/WindowsPresenceSource.hpp"
#else
#include "platform/linux/FreedesktopNotificationDriver.hpp"
#include "platform/linux/LinuxPresenceSource.hpp"
#endif

namespace kodosi {

#ifdef Q_OS_WIN
using SystemNotificationDriver = ToastNotificationDriver;
using SystemPresenceSource = WindowsPresenceSource;
#else
using SystemNotificationDriver = FreedesktopNotificationDriver;
using SystemPresenceSource = LinuxPresenceSource;
#endif

}
