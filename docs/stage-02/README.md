# Notes
## Line Counting
```kotlin
if (it == '\r') {
    line++; 
    column = 1
} else if (it == '\n') {
    if (index < 2 || source[index - 2] != '\r') {
        line++;
    } 
    column = 1
} else {
    column++
}
```

## BOM (Byte Order Mark)
U+FEFF (\uFEFF)
- FEFF -> Big Endian Encoding
- FFFE -> Little Endian Encoding
