#!/bin/bash

PUBLIC_REPO=../../java-advanced-2025

ITERATIVE_MODULE=$PUBLIC_REPO/modules/info.kgeorgiy.java.advanced.iterative
ITERATIVE_SUBDIR=info/kgeorgiy/java/advanced/iterative
SRC=../java-solutions/info/kgeorgiy/ja/gordienko/iterative/*.java

javadoc -d ../javadoc/iterative -author \
$SRC \
$ITERATIVE_MODULE/$ITERATIVE_SUBDIR/ScalarIP.java
