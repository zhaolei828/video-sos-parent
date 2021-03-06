package com.derder.business.service.impl;

import com.derder.base.BaseDomainService;
import com.derder.business.dao.EmrgContactDAO;
import com.derder.business.dao.UserDAO;
import com.derder.business.emtype.UserGroup;
import com.derder.business.model.EmrgContact;
import com.derder.business.model.QUser;
import com.derder.business.model.User;
import com.derder.business.service.UserService;
import com.derder.common.exception.BusinessException;
import com.derder.common.util.EnableFlag;
import com.derder.common.util.ErrorCode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mysema.query.types.Predicate;
import com.mysema.query.types.expr.BooleanExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * 类 编 号：
 * 类 名 称：
 * 内容摘要：
 * 创建日期：2016-12-16 12:20
 * 编码作者：zhaolei
 */
@Service()
public class UserServiceImpl extends BaseDomainService implements UserService {
    @Autowired
    UserDAO userDAO;

    @Autowired
    EmrgContactDAO emrgContactDAO;

    @Override
    public User checkLogin(String phone, String password) {
        return userDAO.findByUserPhoneAndPasswordAndEnableFlag(phone,password, EnableFlag.Y);
    }

    @Override
    public void addUserAndEmrgContactList(User user, List<EmrgContact> emrgContactList) {
        List<User> userListDB = userDAO.findByUserPhoneAndEnableFlag(user.getUserPhone(),EnableFlag.Y);
        if(null != userListDB && userListDB.size() > 0){
            throw new BusinessException(ErrorCode.USER_REG_EXCEPTION,"####用户已存在,userPhone:"+user.getUserPhone());
        }
        Long userId = generateID();
        if (null == user.getCreateBy() || user.getCreateBy() == 0){
            user.setCreateBy(userId);
        }
        if (null == user.getUpdateBy() || user.getUpdateBy() == 0){
            user.setUpdateBy(userId);
        }
        Timestamp now = new Timestamp(new Date().getTime());
        user.setId(userId);
        user.setCreateBy(user.getCreateBy());
        user.setCreateTime(now);
        user.setUpdateBy(user.getUpdateBy());
        user.setUpdateTime(now);
        userDAO.save(user);
        for (EmrgContact emrgContact : emrgContactList){
            Long contactId = generateID();
            if (null == emrgContact.getCreateBy() || emrgContact.getCreateBy() == 0){
                emrgContact.setCreateBy(userId);
            }
            if (null == emrgContact.getUpdateBy() || emrgContact.getUpdateBy() == 0){
                emrgContact.setUpdateBy(userId);
            }
            emrgContact.setId(contactId);
            emrgContact.setCreateBy(emrgContact.getCreateBy());
            emrgContact.setCreateTime(now);
            emrgContact.setUpdateBy(emrgContact.getUpdateBy());
            emrgContact.setUpdateTime(now);
            emrgContact.setBandUser(userId);
        }
        emrgContactDAO.save(emrgContactList);
    }

    @Override
    public void updateUserAndEmrgContactList(User user, List<EmrgContact> emrgContactList) {
        User  userDB = userDAO.findByIdAndEnableFlag(user.getId(),EnableFlag.Y);
        if(null == userDB){
            throw new BusinessException(ErrorCode.USER_NOT_EXIST_EXCEPTION,"####用户不存在,userId:"+user.getId());
        }
        if (!Strings.isNullOrEmpty(user.getUserName())){
            userDB.setUserName(user.getUserName());
        }
        if (!Strings.isNullOrEmpty(user.getUserEmail())){
            userDB.setUserEmail(user.getUserEmail());
        }
        if (null != user.getUpdateBy() || user.getUpdateBy()>0){
            userDB.setUpdateBy(user.getUpdateBy());
        }
        userDB.setUpdateTime(new Date());
        userDAO.save(userDB);
        for (EmrgContact emrgContact : emrgContactList){
            EmrgContact emrgContactDB = emrgContactDAO.findByIdAndEnableFlag(emrgContact.getId(),EnableFlag.Y);
            if(null != emrgContactDB){
                if (!Strings.isNullOrEmpty(emrgContact.getName())){
                    emrgContactDB.setName(emrgContact.getName());
                }
                if (!Strings.isNullOrEmpty(emrgContact.getPhone())){
                    emrgContactDB.setPhone(emrgContact.getPhone());
                }
                if (!Strings.isNullOrEmpty(emrgContact.getEmail())){
                    emrgContactDB.setEmail(emrgContact.getEmail());
                }
                if (null != emrgContact.getUpdateBy() || emrgContact.getUpdateBy()>0){
                    emrgContactDB.setUpdateBy(emrgContact.getUpdateBy());
                }
                emrgContactDB.setUpdateTime(new Date());
            }
            emrgContactDAO.save(emrgContactDB);
        }
    }

    @Override
    public User getUser(long userId) {
        return userDAO.findOne(userId);
    }

    @Override
    public void delUser(User user) {
        user.setEnableFlag(EnableFlag.N);
        userDAO.save(user);
    }

    @Override
    public List<EmrgContact> getEmrgContactListByUser(long userId) {
        return emrgContactDAO.findByBandUserAndEnableFlag(userId,EnableFlag.Y);
    }

    @Override
    public Page<User> listBySearch(String userNameKw, String phoneNumber, String email, String startDate, String endDate, PageRequest pageRequest) {
        List<BooleanExpression> predicates = Lists.newArrayList();
        predicates.add(isEnable(EnableFlag.Y));
        predicates.add(isCommonUser(UserGroup.COMMON_USER));
        if(!Strings.isNullOrEmpty(userNameKw)) {
            predicates.add(userNameIsLike(userNameKw));
        }
        if(!Strings.isNullOrEmpty(phoneNumber)) {
            predicates.add(phoneNumberEq(phoneNumber));
        }
        if(!Strings.isNullOrEmpty(email)) {
            predicates.add(emailEq(email));
        }
        BooleanExpression[] booleanExpressions = predicates.toArray(new BooleanExpression[predicates.size()]);
        Predicate predicate = BooleanExpression.allOf(booleanExpressions);
        Page<User> page = userDAO.findAll(predicate,pageRequest);
        return page;
    }

    private static BooleanExpression isEnable(final EnableFlag enableFlag) {
        return QUser.user.enableFlag.eq(enableFlag);
    }

    private static BooleanExpression isCommonUser(final UserGroup userGroup) {
        return QUser.user.userGroup.eq(userGroup);
    }

    private static BooleanExpression userNameIsLike(final String searchTerm) {
        return QUser.user.userName.contains(searchTerm);
    }

    private static BooleanExpression phoneNumberEq(final String phoneNumber) {
        return QUser.user.userPhone.eq(phoneNumber);
    }

    private static BooleanExpression emailEq(final String email) {
        return QUser.user.userEmail.eq(email);
    }
}
