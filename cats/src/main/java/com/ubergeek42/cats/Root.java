package com.ubergeek42.cats;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;

@Retention(RetentionPolicy.CLASS)
@Target(FIELD)
public @interface Root {}
