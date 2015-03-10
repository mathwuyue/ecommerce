#!/bin/sh

JS_DIR="${HOME}/workspace/cbg"

rsync ${JS_DIR}/cbg.js .
rsync -r ${JS_DIR}/out .
