package net.linlan.emis.federated.repository;

import net.linlan.emis.federated.repository.entity.EmisTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmisTenantMapper {

    @Select("SELECT * FROM emis_tenant WHERE tenant_id = #{tenantId} AND status = '0'")
    EmisTenant findUsable(@Param("tenantId") Long tenantId);
}