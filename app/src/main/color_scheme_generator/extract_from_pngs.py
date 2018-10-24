import os
from PIL import Image

TEMPLATE = """# suppress inspection "UnusedProperty" for whole file
name = {name}

# 0f1315 is the color of the default background of status line
default = {default}
default_bg = {default_bg}

# unimportant stuff
chat_time = {unimportant}
chat_prefix_more = {unimportant}
chat_prefix_action = {unimportant}
chat_inactive_buffer = {unimportant}
chat_read_marker = {unimportant}

# miscellaneous
chat_buffer = {default}
chat_channel = {default}
chat_nick_prefix = {default}
chat_nick_suffix = {default}
chat_delimiters = {default}

chat_nick_self = {nick_self}

# highlights are black on some kind of a reddish color
chat_highlight = {chat_highlight}
chat_highlight_bg = {chat_highlight_bg}\n\n"""

TEMPLATE_UI = """# colors for coloring the ui elements
primary = {primary}
primary_dark = {primary_dark}\n\n"""

############################################################################################################## squirrely

name = "Squirrely Dark"
default = "0xdddddd"
default_bg = "0x0f1315"         # default background for the app
unimportant = "0x3b3f42"
chat_highlight = "0x000000"
chat_highlight_bg = "0xff5f5f"
nick_self = "0xffffff"
DARK = TEMPLATE.format(**locals())

name = "Squirrely Light"
default = "0x444444"
default_bg = "0xffffff"     # default background for the app
unimportant = "0xcccccc"
chat_highlight = "0x000000"
chat_highlight_bg = "0xff7f7f"
nick_self = "0x000000"
LIGHT = TEMPLATE.format(**locals())

name = "Squirrely Light Darker"
default = "0x333333"
LIGHT_DARKER = TEMPLATE.format(**locals())

##################################################################################### https://github.com/morhetz/gruvbox

name = "Gruvbox Dark"
default = "0xebdbb2"            # fg
default_bg = "0x282828"         # bg
unimportant = "0x3c3836"        # bg1
chat_highlight = "0x282828"     # bg
chat_highlight_bg = "0xfe8019"  # orange
nick_self = "0xd65d0e"          # orange dark
primary = "0xff1d2021"          # bg0_h
primary_dark = "0xff1d2021"     # bg0_h
GRUVBOX_DARK = (TEMPLATE + TEMPLATE_UI).format(**locals())

name = "Gruvbox Light"
default = "0x2c2836"            # fg
default_bg = "0xfbf1c7"         # bg
unimportant = "0xebdbb2"        # bg1
chat_highlight = "0x282828"     # bg (gruvbox dark)
chat_highlight_bg = "0xfe8019"  # orange (gruvbox dark)
nick_self = "0xaf3a03"          # orange dark
primary = "0xffebdbb2"          # bg1
primary_dark = "0xffebdbb2"     # bg0_h
GRUVBOX_LIGHT = (TEMPLATE + TEMPLATE_UI).format(**locals())

########################################################################################################################


def process(input_file: str, output_file, fg: bool, width_grid=8, height_grid=32):
    im = Image.open(input_file, 'r')
    width, height = im.size
    pixel_values = list(im.getdata())
    print(f"file: {input_file}, width: {width}, height: {height}")
    width_step = width // width_grid
    height_step = height // height_grid
    left = width_step // 2
    top = height_step // 2
    for y in range(height_grid):
        for x in range(width_grid):
            yy = top + y * height_step
            xx = left + x * width_step
            pixel = pixel_values[width * yy + xx]
            color = pixel[0] << 16 | pixel[1] << 8 | pixel[2]
            output_file.write(f"color{width_grid*y+x}{'' if fg else '_bg'} = {color:#08x}\n")
    print("done")


def run():
    print(f"working directory: {os.getcwd()}")
    with open("../assets/squirrely-dark-theme.properties", "w") as output_file:
        output_file.write(DARK)
        process("dark background.png", output_file, False)
        process("dark foreground.png", output_file, True)
    with open("../assets/squirrely-light-theme.properties", "w") as output_file:
        output_file.write(LIGHT)
        process("light background.png", output_file, False)
        process("light foreground.png", output_file, True)
    with open("../assets/squirrely-light-darker-theme.properties", "w") as output_file:
        output_file.write(LIGHT_DARKER)
        process("light background.png", output_file, False)
        process("light foreground 2.png", output_file, True)
    with open("../assets/gruvbox-dark-theme.properties", "w") as output_file:
        output_file.write(GRUVBOX_DARK)
        process("gb dark background.png", output_file, False)
        process("gb dark foreground.png", output_file, True)
    with open("../assets/gruvbox-light-theme.properties", "w") as output_file:
        output_file.write(GRUVBOX_LIGHT)
        process("gb light background.png", output_file, False)
        process("gb light foreground.png", output_file, True)


if __name__ == "__main__":
    run()
