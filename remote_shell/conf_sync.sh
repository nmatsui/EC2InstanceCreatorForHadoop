#!/bin/sh

USER="hadoop"
CONF_DIR="/usr/local/hadoop/conf"
SLAVES=`cat /usr/local/hadoop/conf/slaves`

for SLAVE in ${SLAVES[@]}; do
  rsync -av $CONF_DIR/ $USER@$SLAVE:$CONF_DIR/
done
