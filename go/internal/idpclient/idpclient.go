// Package idpclient 让叶子 DLL 通过 C 函数接口调用控制层 DLL 内的唯一 IDP 内存栈。
// 约定：全进程只有一个 IDP 栈，位于 controller.dll；叶子 DLL 不再各自持有栈。
package idpclient

import (
	"os"
	"path/filepath"
	"sync"
	"syscall"
	"unsafe"
)

var (
	once    sync.Once
	push    *syscall.LazyProc
	process *syscall.LazyProc
	pop     *syscall.LazyProc
	size    *syscall.LazyProc
	loadErr error
)

// searchDirs 依次尝试：%HDLL% 及其子目录 / 当前进程目录 / 当前工作目录。
func searchDirs() []string {
	var dirs []string
	for _, k := range []string{"HDLL", "HDLL_DIR", "MD_HDLL"} {
		if v := os.Getenv(k); v != "" {
			dirs = append(dirs, v)
			for _, sub := range []string{"app", "core", "doc", "tools"} {
				dirs = append(dirs, filepath.Join(v, sub))
			}
		}
	}
	if exe, err := os.Executable(); err == nil {
		dirs = append(dirs, filepath.Dir(exe))
	}
	if wd, err := os.Getwd(); err == nil {
		dirs = append(dirs, wd)
	}
	return dirs
}

func controllerPath() (string, error) {
	for _, d := range searchDirs() {
		p := filepath.Join(d, "controller.dll")
		if _, err := os.Stat(p); err == nil {
			return p, nil
		}
	}
	// 交给 LoadLibrary 按默认搜索路径再试一次
	return "controller.dll", nil
}

func load() {
	path, err := controllerPath()
	if err != nil {
		loadErr = err
		return
	}
	dll := syscall.NewLazyDLL(path)
	if err := dll.Load(); err != nil {
		loadErr = err
		return
	}
	push = dll.NewProc("idp_push")
	process = dll.NewProc("app_idp_process")
	pop = dll.NewProc("idp_pop_json")
	size = dll.NewProc("idp_stack_size")
}

// Available 报告 controller.dll 是否可用。
func Available() bool {
	once.Do(load)
	return loadErr == nil
}

// Push 压入请求包（队尾）。
func Push(typ, source, data string) int {
	once.Do(load)
	if loadErr != nil {
		return -1
	}
	t, _ := syscall.BytePtrFromString(typ)
	s, _ := syscall.BytePtrFromString(source)
	d, _ := syscall.BytePtrFromString(data)
	r, _, _ := push.Call(0, uintptr(unsafe.Pointer(t)), uintptr(unsafe.Pointer(s)), uintptr(unsafe.Pointer(d)))
	return int(r)
}

// Process 触发控制层处理最新请求。
func Process() int {
	once.Do(load)
	if loadErr != nil {
		return -1
	}
	r, _, _ := process.Call(0)
	return int(r)
}

// PopJSON 弹出队首结果 JSON。
func PopJSON() (string, bool) {
	once.Do(load)
	if loadErr != nil {
		return "", false
	}
	buf := make([]byte, 1<<20)
	n, _, _ := pop.Call(0, uintptr(unsafe.Pointer(&buf[0])), uintptr(len(buf)))
	if n == 0 {
		return "", false
	}
	return string(buf[:n]), true
}

// StackSize 当前栈内包数量。
func StackSize() int {
	once.Do(load)
	if loadErr != nil {
		return -1
	}
	r, _, _ := size.Call(0)
	return int(r)
}

// Call 三段式：push → process → pop。
func Call(typ, data string) (string, bool) {
	if Push(typ, "leaf", data) != 0 {
		return "", false
	}
	Process()
	return PopJSON()
}
