#include "platform/windows/WindowsPresenceSource.hpp"

#include <QMetaObject>

#include <utility>

#include <windows.h>

#include <wtsapi32.h>

namespace kodosi {
namespace {

constexpr wchar_t windowClass[] = L"KodosiPresence";
// GUID_SESSION_DISPLAY_STATUS: the display of this session is off, on or dimmed.
constexpr GUID sessionDisplayStatus { 0x2b84c20e, 0xad23, 0x4ddf, { 0x93, 0xdb, 0x05, 0xff, 0xbd, 0x7e, 0xfc, 0xa5 } };

LRESULT CALLBACK windowProcedure(HWND window, UINT message, WPARAM parameter, LPARAM detail)
{
    if (auto* source = reinterpret_cast<WindowsPresenceSource*>(GetWindowLongPtrW(window, GWLP_USERDATA)))
        source->receive(message, parameter, detail);
    if (message == WM_POWERBROADCAST)
        return TRUE;
    return DefWindowProcW(window, message, parameter, detail);
}

}

WindowsPresenceSource::WindowsPresenceSource(QObject* parent)
    : PresenceSource(parent)
{
    const auto instance = GetModuleHandleW(nullptr);
    WNDCLASSEXW type {};
    type.cbSize = sizeof(type);
    type.lpfnWndProc = windowProcedure;
    type.hInstance = instance;
    type.lpszClassName = windowClass;
    RegisterClassExW(&type);
    // A hidden top-level window, because Windows sends suspend and resume only to top-level windows.
    auto* window = CreateWindowExW(0, windowClass, L"", WS_POPUP, 0, 0, 0, 0, nullptr, nullptr, instance, nullptr);
    if (!window)
        return;
    m_window = window;
    SetWindowLongPtrW(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(this));
    WTSRegisterSessionNotification(window, NOTIFY_FOR_THIS_SESSION);
    m_displayNotices = RegisterPowerSettingNotification(window, &sessionDisplayStatus, DEVICE_NOTIFY_WINDOW_HANDLE);
}

WindowsPresenceSource::~WindowsPresenceSource()
{
    if (!m_window)
        return;
    watchInput(false);
    if (m_displayNotices)
        UnregisterPowerSettingNotification(static_cast<HPOWERNOTIFY>(m_displayNotices));
    auto* window = static_cast<HWND>(m_window);
    WTSUnRegisterSessionNotification(window);
    SetWindowLongPtrW(window, GWLP_USERDATA, 0);
    DestroyWindow(window);
}

PresenceSource::Input WindowsPresenceSource::input()
{
    LASTINPUTINFO last {};
    last.cbSize = sizeof(last);
    if (!GetLastInputInfo(&last))
        return { .quiet = false, .quietIn = m_awayTime };
    const std::chrono::milliseconds age(static_cast<DWORD>(GetTickCount() - last.dwTime));
    const bool quiet = age >= m_awayTime;
    watchInput(quiet);
    return { .quiet = quiet, .quietIn = quiet ? std::nullopt : std::optional(m_awayTime - age) };
}

void WindowsPresenceSource::setAwayTime(const std::chrono::milliseconds time)
{
    m_awayTime = time;
}

void WindowsPresenceSource::receive(const unsigned int message, const std::uintptr_t parameter, const std::intptr_t detail)
{
    if (message == WM_WTSSESSION_CHANGE && parameter == WTS_SESSION_LOCK) {
        set(m_locked, true);
    } else if (message == WM_WTSSESSION_CHANGE && parameter == WTS_SESSION_UNLOCK) {
        set(m_locked, false);
    } else if (message == WM_POWERBROADCAST && parameter == PBT_APMSUSPEND) {
        set(m_sleeping, true);
    } else if (message == WM_POWERBROADCAST && (parameter == PBT_APMRESUMESUSPEND || parameter == PBT_APMRESUMEAUTOMATIC)) {
        set(m_sleeping, false);
    } else if (message == WM_POWERBROADCAST && parameter == PBT_POWERSETTINGCHANGE) {
        const auto* setting = reinterpret_cast<const POWERBROADCAST_SETTING*>(detail);
        if (setting && IsEqualGUID(setting->PowerSetting, sessionDisplayStatus) && setting->DataLength == sizeof(DWORD))
            set(m_displayOff, *reinterpret_cast<const DWORD*>(setting->Data) == 0);
    } else if (message == WM_INPUT && m_watchingInput) {
        watchInput(false);
        QMetaObject::invokeMethod(this, &PresenceSource::changed, Qt::QueuedConnection);
    }
}

// Windows tells the time of the last input but not the input itself, so the next input after a quiet
// time comes as raw input, also while another app is in front.
void WindowsPresenceSource::watchInput(const bool watch)
{
    if (!m_window || m_watchingInput == watch)
        return;
    const auto flags = watch ? RIDEV_INPUTSINK : RIDEV_REMOVE;
    auto* target = watch ? static_cast<HWND>(m_window) : nullptr;
    RAWINPUTDEVICE devices[] {
        { .usUsagePage = 0x01, .usUsage = 0x02, .dwFlags = static_cast<DWORD>(flags), .hwndTarget = target },
        { .usUsagePage = 0x01, .usUsage = 0x06, .dwFlags = static_cast<DWORD>(flags), .hwndTarget = target },
    };
    if (RegisterRawInputDevices(devices, 2, sizeof(RAWINPUTDEVICE)))
        m_watchingInput = watch;
}

void WindowsPresenceSource::set(bool& fact, const bool value)
{
    if (std::exchange(fact, value) != value)
        QMetaObject::invokeMethod(this, &PresenceSource::changed, Qt::QueuedConnection);
}

}
