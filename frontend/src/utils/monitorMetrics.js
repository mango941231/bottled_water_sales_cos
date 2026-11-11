// External Tomcat and different JVMs do not register every optional metric.
export function getMetrics (get, names) {
  return get('actuator/metrics').then(response => {
    const available = response.data.names || []
    return Promise.all(names.map(name => {
      const missing = () => ({data: {name, unavailable: true, measurements: [{value: '-'}, {value: '-'}]}})
      if (!available.includes(name)) return missing()
      return get(`actuator/metrics/${name}`).catch(error => {
        if (error.response && error.response.status === 404) return missing()
        throw error
      })
    }))
  })
}
