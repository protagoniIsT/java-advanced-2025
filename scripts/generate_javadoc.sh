#!/bin/bash

PUBLIC_REPO=../../java-advanced-2025

IMPLEMENTOR_MODULE=$PUBLIC_REPO/modules/info.kgeorgiy.java.advanced.implementor
IMPLEMENTOR_TOOLS_MODULE=$PUBLIC_REPO/modules/info.kgeorgiy.java.advanced.implementor.tools
IMPLEMENTOR_SUBDIR=info/kgeorgiy/java/advanced/implementor
SRC=../java-solutions/info/kgeorgiy/ja/gordienko/implementor/*.java

javadoc -d ../javadoc -private -author \
$SRC \
$IMPLEMENTOR_MODULE/$IMPLEMENTOR_SUBDIR/ImplerException.java \
$IMPLEMENTOR_MODULE/$IMPLEMENTOR_SUBDIR/Impler.java \
$IMPLEMENTOR_TOOLS_MODULE/$IMPLEMENTOR_SUBDIR/tools/JarImpler.java
