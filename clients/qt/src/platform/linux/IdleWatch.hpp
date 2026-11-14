#pragma once

#include <chrono>
#include <functional>
#include <memory>

namespace kodosi {

// The desktop's own signal for no input on this computer for a time, and for the next input after it.
class IdleWatch {
public:
    using Quiet = std::function<void(bool quiet)>;

    virtual ~IdleWatch() = default;
    virtual void watch(std::chrono::milliseconds quietTime) = 0;
};

[[nodiscard]] std::unique_ptr<IdleWatch> waylandIdleWatch(IdleWatch::Quiet quiet);
[[nodiscard]] std::unique_ptr<IdleWatch> mutterIdleWatch(IdleWatch::Quiet quiet);
[[nodiscard]] std::unique_ptr<IdleWatch> x11IdleWatch(IdleWatch::Quiet quiet);

}
