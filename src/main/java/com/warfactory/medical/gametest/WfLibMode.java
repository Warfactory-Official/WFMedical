package com.warfactory.medical.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Test registered only when WFLib presence == {@link #value}; absent annotation => both modes. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface WfLibMode {
    boolean value();
}
