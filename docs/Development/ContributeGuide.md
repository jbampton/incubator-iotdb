<!--

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.

-->

# 一、work process

## Main link

IoTDB official website：https://iotdb.apache.org/

Code library：https://github.com/apache/incubator-iotdb/tree/master

Get started quickly：http://iotdb.apache.org/UserGuide/master/0-Get%20Started/1-QuickStart.html

## Subscribe to mailing list

The mailing list is where the Apache project conducts technical discussions and communication with users. Follow the mailing list to receive mail.

Mailing list address：dev@iotdb.apache.org

Follow method: Send an email to dev-subscribe@iotdb.apache.org with the email you want to receive the email, the subject content is not limited, after receiving the reply, send a confirmation email to the confirmation address again (the confirmation address is longer, it is recommended  gmail mailbox).


Other mailing list:
* notifications@iotdb.apache.org (for JIRA information notification.)
  * If you just want to pay attention to some issues, you do not need to subscribe this mailing list.
  Instead, you just need to click "start-watching this issue" on the jira issue webpage. 
* commits@iotdb.apache.org (for code changes notification. Take care because this mailing list may have many emails)
* reviews@iotdb.apache.org (for code reviews notification on Github.  Take care because this mailing list may have many emails)



## New features, bug feedback, improvements, and more

All features or bugs that you want IoTDB to do can be raised on Jira：https://issues.apache.org/jira/projects/IOTDB/issues

You can choose issue types: bug, improvement, new feature, etc.  New issues will be automatically synchronized to the mailing list (notifications@), and subsequent discussions can be left on jira or on the mailing list.  When the issue is resolved, close the issue.

## Email discussion content (English)

* Joining the mailing list for the first time can introduce you briefly.  (Hi, I'm xxx ...)

* Before developing a function, you can send an e-mail to declare the task you want to do.（Hi，I'm working on issue IOTDB-XXX，My plan is ...）

## Contributing documents

The content of all IoTDB official websites is in the docs of the project root directory:

* docs/SystemDesign: System Design Document-English Version
* docs/zh/SystemDesign: System Design Document-Chinese Version
* docs/UserGuide: User Guide English Version
* docs/zh/UserGuide: User Guide Chinese Version
* docs/Community: community English Version
* docs/zh/Community: community Chinese Version
* docs/Development: Development Guide English Version
* docs/zh/Development: Development Guide Chinese Version

Correspondence between versions and branches on the official website:

* In progress -> master
* major_version.x -> rel/major_version （如 0.9.x -> rel/0.9）

Precautions:

* Images in Markdown can be uploaded to https://github.com/thulab/iotdb/issues/543 for url

## Contributing code

You can go to jira to pick up the existing issue or create your own issue and get it. The comment says that I can do this issue.

* Clone the repository to your own local repository, clone to the local, and associate the apache repository as the upstream upstream repository.
* Cut out a new branch from master. The branch name is determined by the function of this branch. It is usually called f_new_feature (such as f_storage_engine) or fix_bug (such as fix_query_cache_bug).
* Add code style as the root java-google-style.xml in the idea
* Modify the code and add test cases (unit test, integration test)
  * Integration test reference:server/src/test/java/org/apache/iotdb/db/integration/IoTDBTimeZoneIT
* Submit a PR, starting with [IOTDB-jira number]
* Email to dev mailing list：(I've submitted a PR for issue IOTDB-xxx [link])
* Make changes based on other people's reviews and continue to update until merged
* close jira issue

## 二、IoTDB debugging method

Recommended Use Intellij idea。```mvn clean package -DskipTests``` After putting ```server/target/generated-sources/antlr4``` and ```service-rpc/target/generated-sources/thrift``` marked as ```Source Root```。 

* Server main function：```server/src/main/java/org/apache/iotdb/db/service/IoTDB```，Can be started in debug mode
* Client：```client/src/main/java/org/apache/iotdb/client/```，Use Clinet for linux and WinClint for windows, you can start directly, need the parameter "-h 127.0.0.1 -p 6667 -u root -pw root"
* Server rpc implementation (mainly used for client and server communication, generally start interruption point here):```server/src/main/java/org/apache/iotdb/db/service/TSServiceImpl```
  * all jdbc statements：executeStatement(TSExecuteStatementReq req)
  * jdbc query：executeQueryStatement(TSExecuteStatementReq req)	
  * native Write interface：insertRecord(TSInsertRecordReq req)

* Storage engine org.apache.iotdb.db.engine.StorageEngine
* Query engine org.apache.iotdb.db.qp.QueryProcessor
