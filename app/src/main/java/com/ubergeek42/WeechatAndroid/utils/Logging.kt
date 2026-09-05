package com.ubergeek42.WeechatAndroid.utils

import cats.ModuleLogger
import cats.androidLogger

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "unused") // java.lang.String, packageName
@ModuleLogger val logger = androidLogger { packageName -> "App" }