package net.linlan.emis.user.mapper;

import net.linlan.emis.user.entity.EmisUser;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EmisUserMapper {

    @Select("SELECT * FROM emis_user WHERE user_name = #{userName} AND del_flag = '0'")
    EmisUser findByUserName(@Param("userName") String userName);

    @Select("SELECT * FROM emis_user WHERE user_id = #{userId} AND del_flag = '0'")
    EmisUser findById(@Param("userId") Long userId);

    @Select("SELECT * FROM emis_user WHERE del_flag = '0'")
    List<EmisUser> findAll();

    @Insert("INSERT INTO emis_user (tenant_id, role_level, user_name, nick_name, email, phonenumber, " +
            "avatar_url, password, status, del_flag, created_by, created_at) " +
            "VALUES (#{tenantId}, #{roleLevel}, #{userName}, #{nickName}, #{email}, #{phonenumber}, " +
            "#{avatarUrl}, #{password}, #{status}, '0', #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(EmisUser user);

    @Update("UPDATE emis_user SET nick_name = #{nickName}, email = #{email}, phonenumber = #{phonenumber}, " +
            "status = #{status}, updated_by = #{updatedBy}, updated_at = NOW() WHERE user_id = #{userId}")
    int update(EmisUser user);

    @Update("UPDATE emis_user SET del_flag = '1' WHERE user_id = #{userId}")
    int softDelete(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM emis_user WHERE del_flag = '0'")
    long count();

    @Select("SELECT * FROM emis_user WHERE user_id = #{userId}")
    EmisUser findByIdIncludeDeleted(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM emis_user WHERE user_name = #{userName}")
    int countByUserNameIncludeDeleted(@Param("userName") String userName);

    @Select("SELECT COUNT(*) FROM emis_user WHERE del_flag = '0' AND LOWER(email) = LOWER(#{email})")
    int countActiveByEmail(@Param("email") String email);

    @Select("SELECT COUNT(*) FROM emis_user WHERE del_flag = '0' AND phonenumber = #{phonenumber}")
    int countActiveByPhonenumber(@Param("phonenumber") String phonenumber);

    @Update("UPDATE emis_user SET " +
            "nick_name = CASE WHEN nick_name IS NULL OR nick_name = '' THEN #{nickName} ELSE nick_name END, " +
            "avatar_url = CASE WHEN avatar_url IS NULL OR avatar_url = '' THEN #{avatarUrl} ELSE avatar_url END, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int fillProfileIfBlank(@Param("userId") Long userId,
                           @Param("nickName") String nickName,
                           @Param("avatarUrl") String avatarUrl);
}
