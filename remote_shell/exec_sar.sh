#!/bin/sh

SAR_INTERVAL=10
SAR_HOUR=5

LANG=C
SAR_NUM=$(( $SAR_HOUR * 60 * 60 / $SAR_INTERVAL ))

BASE_DIR="/mnt/hadoop/script"
SAR_DIR=$BASE_DIR/sar

case $1 in
"start")
  mkdir $SAR_DIR
  sar -A -o $SAR_DIR/$2 $SAR_INTERVAL $SAR_NUM < /dev/null > /dev/null 2> /dev/null &
  echo $!
  ;;
"stop")
  kill -9 $2
  ;;
*)
  echo "argument error"
  ;;
esac
