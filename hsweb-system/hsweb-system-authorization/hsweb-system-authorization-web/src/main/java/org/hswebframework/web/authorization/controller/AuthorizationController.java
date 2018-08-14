/*
 * Copyright 2016 http://www.hswebframework.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.hswebframework.web.authorization.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.hswebframework.web.BusinessException;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.WebUtil;
import org.hswebframework.web.authorization.Authentication;
import org.hswebframework.web.authorization.AuthenticationManager;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.authorization.listener.event.*;
import org.hswebframework.web.commons.entity.DataStatus;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.entity.authorization.UserEntity;
import org.hswebframework.web.logging.AccessLogger;
import org.hswebframework.web.service.authorization.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.hswebframework.web.controller.message.ResponseMessage.ok;

/**
 * @author zhouhao
 */
@RestController
@RequestMapping("${hsweb.web.mappings.authorize:authorize}")
@AccessLogger("授权")
@Api(tags = "权限-用户授权", value = "授权")
public class AuthorizationController {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @GetMapping({"/login-out", "/sign-out", "/exit"})
    @Authorize
    @ApiOperation("退出当前登录")
    public ResponseMessage exit(@ApiParam(hidden = true) Authentication authentication) {
        eventPublisher.publishEvent(new AuthorizationExitEvent(authentication));
        return ok();
    }

    @GetMapping("/me")
    @Authorize
    @ApiOperation("当前登录用户权限信息")
    public ResponseMessage<Authentication> me(@ApiParam(hidden = true) Authentication authentication) {
        return ok(authentication);
    }


    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation("用户名密码登录,json方式")
    public ResponseMessage<Map<String, Object>> authorize(@ApiParam(example = "{\"username\":\"admin\",\"password\":\"admin\"}")
                                                          @RequestBody Map<String, String> parameter) {

        return doLogin(Objects.requireNonNull(parameter.get("username"), "用户名不能为空")
                , Objects.requireNonNull(parameter.get("password"), "密码不能为空")
                , parameter);
    }

    @PostMapping(value = "/login",consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ApiOperation("用户名密码登录,参数方式")
    public ResponseMessage<Map<String, Object>> authorize(@RequestParam @ApiParam("用户名") String username,
                                                          @RequestParam @ApiParam("密码") String password,
                                                          @ApiParam(hidden = true) HttpServletRequest request) {

        return doLogin(username, password, WebUtil.getParameters(request));
    }

    protected ResponseMessage<Map<String, Object>> doLogin(String username, String password, Map<String, ?> parameter) {
        AuthorizationFailedEvent.Reason reason = AuthorizationFailedEvent.Reason.OTHER;
        Function<String, Object> parameterGetter = parameter::get;
        try {
            AuthorizationDecodeEvent decodeEvent = new AuthorizationDecodeEvent(username, password, parameterGetter);
            eventPublisher.publishEvent(decodeEvent);
            username = decodeEvent.getUsername();
            password = decodeEvent.getPassword();
            AuthorizationBeforeEvent beforeEvent = new AuthorizationBeforeEvent(username, password, parameterGetter);
            eventPublisher.publishEvent(beforeEvent);
            UserEntity entity = userService.selectByUserNameAndPassword(username, password);
            if (entity == null) {
                reason = AuthorizationFailedEvent.Reason.PASSWORD_ERROR;
                throw new NotFoundException("密码错误");
            }
            if (!DataStatus.STATUS_ENABLED.equals(entity.getStatus())) {
                reason = AuthorizationFailedEvent.Reason.USER_DISABLED;
                throw new BusinessException("{user_is_disabled}", 400);
            }
            // 验证通过
            Authentication authentication = authenticationManager.getByUserId(entity.getId());
            //触发授权成功事件
            AuthorizationSuccessEvent event = new AuthorizationSuccessEvent(authentication, parameterGetter);
            event.getResult().put("userId", entity.getId());
            eventPublisher.publishEvent(event);
            return ok(event.getResult());
        } catch (Exception e) {
            AuthorizationFailedEvent failedEvent = new AuthorizationFailedEvent(username, password, parameterGetter, reason);
            failedEvent.setException(e);
            eventPublisher.publishEvent(failedEvent);
            throw e;
        }
    }

}
