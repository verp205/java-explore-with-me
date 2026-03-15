package ru.practicum.statservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.statservice.model.EndpointHit;

@Mapper(componentModel = "spring")
public interface EndpointHitMapper {
    @Mapping(target = "id", ignore = true)
    EndpointHit mapToEndpointHit(EndpointHitDto newEndpointHitDto);
}
