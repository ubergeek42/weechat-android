def gen(fg=True):
    out = ""
    for y in range(32):
        out += "/eval /print "
        for x in range(8):
            out += "${color:"
            if not fg:
                out += str(y * 8 + x)
                out += ","
            index = y * 8 + x
            out += str(index)
            out += "}"
            out += f"{index:03}"
        out += "\n"
    return out


print(gen(True) + gen(False))

