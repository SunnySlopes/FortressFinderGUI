//
// Created by zhdds on 2026/1/20.
//

#ifndef CUBIOMES_THREAD_H
#define CUBIOMES_THREAD_H
#include <queue>
#include <mutex>
#include <thread>
#include <functional>
#include <condition_variable>
#include <atomic>
#include <iostream>

template<typename T>
class ThreadSafeResults {
    std::priority_queue<T> results_;
    mutable std::mutex mutex_{};

public:
    void addResult(const T &res)
    {
        std::lock_guard<std::mutex> lock(mutex_);
        results_.emplace(res);
    }

    void addResults(const std::vector<T> &newResults)
    {
        std::lock_guard<std::mutex> lock(mutex_);
        for (const auto &result: newResults)
        {
            results_.emplace(result);
        }
    }

    const T &get() const
    {
        return results_.top();
    }

    bool empty() const
    {
        return results_.empty();
    }

    void clear()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        while (!results_.empty())
            results_.pop();
    }

    std::vector<T> peekAll() const
    {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<T> copy;
        std::priority_queue<T> temp = results_;
        while (!temp.empty()) {
            copy.push_back(temp.top());
            temp.pop();
        }
        return copy;
    }

    std::vector<T> getAllResults()
    {
        std::lock_guard<std::mutex> lock(mutex_);
        std::vector<T> allResults;
        while (!results_.empty())
        {
            allResults.push_back(results_.top());
            results_.pop();
        }
        std::cout << "addResults: " << allResults.size() << "\n";
        return allResults;
    }
};

class ThreadPool {
private:
    std::vector<std::thread> workers;
    std::queue<std::function<void()> > tasks;
    std::mutex queueMutex;
    std::condition_variable condition;
    std::atomic<bool> stop{false};

public:
    explicit ThreadPool(size_t threads)
    {
        for (size_t i = 0; i < threads; ++i)
        {
            workers.emplace_back([this] {
                while (true)
                {
                    std::function<void()> task;
                    {
                        std::unique_lock<std::mutex> lock(this->queueMutex);
                        this->condition.wait(lock, [this] {
                            return this->stop || !this->tasks.empty();
                        });
                        if (this->stop && this->tasks.empty())
                            return;
                        task = std::move(this->tasks.front());
                        this->tasks.pop();
                    }
                    task();
                }
            });
        }
    }

    template<class F>
    void enqueue(F &&f)
    {
        {
            std::unique_lock<std::mutex> lock(queueMutex);
            tasks.emplace(std::forward<F>(f));
        }
        condition.notify_one();
    }

    ~ThreadPool()
    {
        stop = true;
        condition.notify_all();
        for (std::thread &worker: workers)
        {
            if (worker.joinable())
            {
                worker.join();
            }
        }

        std::cout << "ThreadPool destroyed" << std::endl;
    }
};
#endif //CUBIOMES_THREAD_H
