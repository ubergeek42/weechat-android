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
SQUIRRELY_DARK = TEMPLATE.format(**locals())

name = "Squirrely Light"
default = "0x444444"
default_bg = "0xffffff"     # default background for the app
unimportant = "0xcccccc"
chat_highlight = "0x000000"
chat_highlight_bg = "0xff7f7f"
nick_self = "0x000000"
SQUIRRELY_LIGHT = TEMPLATE.format(**locals())

name = "Squirrely Light Darker"
default = "0x333333"
SQUIRRELY_LIGHT_DARKER = TEMPLATE.format(**locals())

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

################################################################################################################# amoled

name = "Pitch Black"
default = "0xcccccc"
default_bg = "0x0"
unimportant = "0x222222"
chat_highlight = "0x000000"
chat_highlight_bg = "0xff5f5f"
nick_self = "0xffffff"
primary = "0xdd000000"
primary_dark = "0xff000000"
PITCH_BLACK = (TEMPLATE + TEMPLATE_UI).format(**locals())

########################################################################################################################


def process(input_file: str, output_file, fg: bool, width_grid=8, height_grid=32):
    im = Image.open(input_file, 'r')
    width, height = im.size
    pixel_values = list(im.getdata())
    print(f"  reading: {input_file}")
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
            yield f"color{width_grid*y+x}{'' if fg else '_bg'} = {color:#08x}"

def create_theme(name: str, prefix: str, fg: str, bg: str):
    print(f"creating theme: {name}")
    with open(f"../assets/{name}-theme.properties", "w") as file:
        file.write(prefix +
            '\n'.join(process(bg + ".png", file, False)) +
            '\n' +
            '\n'.join(process(fg + ".png", file, True)) +
            '\n')

def run():
    print(f"working directory: {os.getcwd()}")
    create_theme("squirrely-dark", SQUIRRELY_DARK, "s-dark", "s-dark-bg")
    create_theme("squirrely-light", SQUIRRELY_LIGHT, "s-light", "s-light-bg")
    create_theme("squirrely-light-darker", SQUIRRELY_LIGHT_DARKER, "s-light-darker", "s-light-bg")
    create_theme("gruvbox-dark", GRUVBOX_DARK, "g-dark", "g-dark-bg")
    create_theme("gruvbox-light", GRUVBOX_LIGHT, "g-light", "g-light-bg")
    create_theme("pitch-black", PITCH_BLACK, "s-dark", "s-dark-bg")
    print("done")


if __name__ == "__main__":
    run()
