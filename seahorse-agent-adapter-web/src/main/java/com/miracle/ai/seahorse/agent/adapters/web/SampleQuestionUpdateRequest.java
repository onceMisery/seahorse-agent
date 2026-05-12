/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.adapters.web;

/**
 * 示例问题更新请求。
 */
public class SampleQuestionUpdateRequest {

    private String title;
    private String description;
    private String question;
    private boolean titlePresent;
    private boolean descriptionPresent;
    private boolean questionPresent;

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String question() {
        return question;
    }

    public boolean titlePresent() {
        return titlePresent;
    }

    public boolean descriptionPresent() {
        return descriptionPresent;
    }

    public boolean questionPresent() {
        return questionPresent;
    }

    public void setTitle(String title) {
        if (title == null) {
            return;
        }
        this.title = title;
        this.titlePresent = true;
    }

    public void setDescription(String description) {
        if (description == null) {
            return;
        }
        this.description = description;
        this.descriptionPresent = true;
    }

    public void setQuestion(String question) {
        if (question == null) {
            return;
        }
        this.question = question;
        this.questionPresent = true;
    }
}
