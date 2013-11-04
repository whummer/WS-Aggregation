#!/bin/sh
#
# Copyright (c) 2010 Michael Starzinger
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -z "$1" ]; then
	echo Usage: $0 [EBNF-file]
	exit
fi

cat "$1" | \
	sed -e "s/\([A-Z][a-zA-Z]*\)\([\?\+\*]\)/(\1)\2/g" | \
	sed -e "s/\(\"[^\" ]*\"\)\([\?\+\*]\)/(\1)\2/g" | \
	sed -e "s/\([A-Z][a-zA-Z]*\)/p\1()/g" | \
	awk -F '\t' '{ print "/*" $1 "*/ private void " $2 " : {} { " $4 " }" }'
