package net.linlan.emis.federated.repository;

import net.linlan.emis.federated.repository.entity.FederatedIdentity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FederatedIdentityMapper {

    @Insert("INSERT INTO emis_federated_identity (user_id, tenant_id, provider, provider_subject, open_id, " +
            "display_name, avatar_url, status, active_flag, bound_at, login_count, created_at) " +
            "VALUES (#{userId}, #{tenantId}, #{provider}, #{providerSubject}, #{openId}, " +
            "#{displayName}, #{avatarUrl}, #{status}, #{activeFlag}, NOW(), 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "bindingId")
    int insert(FederatedIdentity identity);

    @Select("SELECT * FROM emis_federated_identity " +
            "WHERE provider = #{provider} AND provider_subject = #{providerSubject} AND status = 'active'")
    FederatedIdentity findActiveBySubject(@Param("provider") String provider,
                                          @Param("providerSubject") String providerSubject);

    @Select("SELECT * FROM emis_federated_identity WHERE binding_id = #{bindingId}")
    FederatedIdentity findById(@Param("bindingId") Long bindingId);

    @Select("SELECT * FROM emis_federated_identity " +
            "WHERE user_id = #{userId} AND provider = #{provider} AND status = 'active'")
    FederatedIdentity findActiveByUserAndProvider(@Param("userId") Long userId,
                                                  @Param("provider") String provider);

    @Select("SELECT COUNT(*) FROM emis_federated_identity " +
            "WHERE provider = #{provider} AND provider_subject = #{providerSubject} " +
            "AND status IN ('unbound', 'invalid')")
    int countHistoric(@Param("provider") String provider,
                      @Param("providerSubject") String providerSubject);

    @Update("UPDATE emis_federated_identity SET last_login_at = NOW(), " +
            "login_count = login_count + 1, open_id = COALESCE(#{openId}, open_id), updated_at = NOW() " +
            "WHERE binding_id = #{bindingId} AND status = 'active'")
    int touchLogin(@Param("bindingId") Long bindingId, @Param("openId") String openId);

    @Update("UPDATE emis_federated_identity SET status = 'unbound', active_flag = NULL, " +
            "unbound_at = NOW(), updated_at = NOW() " +
            "WHERE binding_id = #{bindingId} AND user_id = #{userId} AND status = 'active'")
    int markUnbound(@Param("bindingId") Long bindingId, @Param("userId") Long userId);

    @Update("UPDATE emis_federated_identity SET status = 'invalid', active_flag = NULL, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND status = 'active'")
    int invalidateByUser(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM emis_federated_identity WHERE user_id = #{userId} AND status = 'active'")
    int countActiveByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM emis_federated_identity WHERE user_id = #{userId} AND status = 'active' " +
            "ORDER BY bound_at ASC")
    List<FederatedIdentity> listActiveByUser(@Param("userId") Long userId);
}