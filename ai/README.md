# Environment setup

## Prerequisites

- Install CMake
- Install the NDK and set the `ANDROID_NDK` environment variable

## git submodule

Initialize submodules from the repository root:

```bash
git submodule update --init --recursive
```

Note: run this from the git repository root (`orchordsai/`), not from
`src/main/cpp/mnn`.

## Build libMNN.so

Enter `src/main/cpp/mnn` and run:

```bash
./build.sh
```
