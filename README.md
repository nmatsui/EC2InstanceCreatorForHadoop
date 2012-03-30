EC2InstanceCreator for Hadoop
=============================

AWS EC2上に、Hadoopクラスタ用インスタンスを自動的に立ち上げるスクリプト

AWS ElasticMapReduce ではHadoopのバージョンやディストリビュータを選べないため、そのあたりをカスタマイズしたい場合に利用できるかと思います。

なぜかScala 2.8.1で書かれています。2.9.1では動きません。。。
またHadoop用Templateは、Cloudera Distribution for Apache Hadoop3（Apache Hadoop 0.20.2 + 737）用です。違うバージョンのHadoopを用いる場合、Templateを修正する必要があるかもしれません。

使い方
======

事前準備
--------

1. src/main/actor.scala の "OWNER", "ACCESS_KEY", "SECRET_KEY", "END_POINT" を適切に修正する
1. build/build.xml の "scala.home" を適切に修正する
1. bin/exec の"SCALA_HOME"を適切に修正する
1. lib以下に、依存する次のJARファイルを配置する : "aws-java-sdk-1.1.1.jar", "commons-logging-1.0.4.jar", "commons-codec-1.3.jar", "ganymed-ssh2-build210.jar", "commons-httpclient-3.0.jar"
1. buildディレクトリで `ant` を実行

自分が登録しているAMIのリストを取得する
---------------------------------------

1. binディレクトリで `ami_list` を実行する

稼働中のインスタンスのリストを取得する
--------------------------------------

1. binディレクトリで `instance_list` を実行する

Slave数を指定してHadoopクラスタを起動する
-----------------------------------------

1. binディレクトリの run_instance 中の "AMI", "TYPE", "GROUP", "PEM_NAME", "PEM_FILE", "TEMPLATE_DIR" を適切に修正する
1. binディレクトリで `run_instance slave数` を実行する

License
=======
Copyright(C) 2012 Nobuyuki Matsui (nobuyuki.matsui@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
