import { Alert, Box, Button, Card, Chip, Typography } from '@mui/material';
import { BarChart, LineChart, PieChart } from '@mui/x-charts';
import type { A2UICompositePayload, A2UIPayload } from './A2UITypes';

// ─── Chart data helpers ──────────────────────────────────────────────────────

/**
 * Auto-detect x-axis key and numeric value key from an array of data objects.
 * Falls back to { xKey: 'x', yKey: 'y' } if detection fails.
 */
function detectChartKeys(data: Record<string, unknown>[]): { xKey: string; yKey: string } {
  if (data.length === 0) return { xKey: 'x', yKey: 'y' }
  const first = data[0]
  const keys = Object.keys(first)
  const numericKey = keys.find((k) => typeof first[k] === 'number') ?? keys[keys.length - 1]
  const xKey = keys.find((k) => k !== numericKey) ?? keys[0]
  return { xKey, yKey: numericKey }
}

/**
 * Extracts x-axis labels and series data from an array of data objects.
 */
function extractChartData(data: Record<string, unknown>[]) {
  if (data.length === 0) return { xLabels: [] as string[], seriesData: [] as number[] }
  const { xKey, yKey } = detectChartKeys(data)
  return {
    xLabels: data.map((d) => String(d[xKey] ?? '')),
    seriesData: data.map((d) => Number(d[yKey]) || 0),
  }
}

/**
 * Detects if data is a flat array of primitives (labels) or objects.
 */
function isPrimitiveArray(data: unknown): data is (string | number)[] {
  return Array.isArray(data) && data.length > 0 && (typeof data[0] === 'string' || typeof data[0] === 'number')
}

// ─── Component registry ──────────────────────────────────────────────────────

const componentRegistry: Record<string, (payload: A2UIPayload) => React.ReactNode> = {
  line_chart: (p) => {
    const rawData = p.data as Record<string, unknown>[] | undefined
    const chartData = rawData && !isPrimitiveArray(rawData) ? rawData : []
    const { xLabels, seriesData } = extractChartData(chartData)
    return (
      <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-line_chart">
        {p.title && <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>}
        {xLabels.length > 0 && seriesData.length > 0 ? (
          <LineChart
            xAxis={[{ scaleType: 'point', data: xLabels }]}
            series={[{ data: seriesData }]}
            height={300}
          />
        ) : (
          <Typography variant="caption" color="text.secondary">
            [No chart data available]
          </Typography>
        )}
      </Card>
    )
  },

  bar_chart: (p) => {
    const rawData = p.data as Record<string, unknown>[] | undefined
    const chartData = rawData && !isPrimitiveArray(rawData) ? rawData : []
    const { xLabels, seriesData } = extractChartData(chartData)
    return (
      <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-bar_chart">
        {p.title && <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>}
        {xLabels.length > 0 && seriesData.length > 0 ? (
          <BarChart
            xAxis={[{ scaleType: 'band', data: xLabels }]}
            series={[{ data: seriesData }]}
            height={300}
          />
        ) : (
          <Typography variant="caption" color="text.secondary">
            [No chart data available]
          </Typography>
        )}
      </Card>
    )
  },

  pie_chart: (p) => {
    const rawData = p.data as Record<string, unknown>[] | undefined
    const items =
      rawData && !isPrimitiveArray(rawData)
        ? rawData.map((d, i) => {
            const keys = Object.keys(d)
            const labelKey = keys.find((k) => typeof d[k] !== 'number') ?? keys[0]
            const valueKey = keys.find((k) => typeof d[k] === 'number') ?? keys[keys.length - 1]
            return {
              id: i,
              label: String(d[labelKey] ?? ''),
              value: Number(d[valueKey]) || 0,
            }
          })
        : []
    return (
      <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-pie_chart">
        {p.title && <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>}
        {items.length > 0 ? (
          <PieChart series={[{ data: items }]} height={300} />
        ) : (
          <Typography variant="caption" color="text.secondary">
            [No chart data available]
          </Typography>
        )}
      </Card>
    )
  },

  metric_card: (p) => {
    const data = p.data as Record<string, unknown> | undefined
    const unit = data?.unit as string | undefined
    return (
      <Card variant="outlined" sx={{ p: 2, minWidth: 180 }} data-testid="a2ui-metric_card">
        <Typography variant="caption" color="text.secondary">{p.title}</Typography>
        <Typography variant="h4" fontWeight={700}>
          {String(data?.value ?? '—')}
        </Typography>
        {unit != null && (
          <Typography variant="caption">{unit}</Typography>
        )}
      </Card>
    )
  },

  stat_card: (p) => (
    <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-stat_card">
      <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>
      <Typography variant="body2">{JSON.stringify(p.data)}</Typography>
    </Card>
  ),

  data_table: (p) => {
    const rows = Array.isArray(p.data) ? (p.data as Record<string, unknown>[]) : []
    const columns = rows.length > 0 ? Object.keys(rows[0]) : []
    return (
      <Card variant="outlined" sx={{ p: 2, overflowX: 'auto' }} data-testid="a2ui-data_table">
        {p.title && <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>}
        {rows.length > 0 ? (
          <Box component="table" sx={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {columns.map((col) => (
                  <Box key={col} component="th" sx={{ textAlign: 'left', p: 1, borderBottom: 2, borderColor: 'divider', fontWeight: 600, fontSize: '0.875rem' }}>
                    {col}
                  </Box>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, i) => (
                <tr key={i}>
                  {columns.map((col) => (
                    <Box key={col} component="td" sx={{ p: 1, borderBottom: 1, borderColor: 'grey.200', fontSize: '0.875rem' }}>
                      {String(row[col] ?? '')}
                    </Box>
                  ))}
                </tr>
              ))}
            </tbody>
          </Box>
        ) : (
          <Typography variant="caption" color="text.secondary">
            [No data available]
          </Typography>
        )}
      </Card>
    )
  },

  progress_bar: (p) => (
    <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-progress_bar">
      <Typography variant="subtitle2">{p.title}</Typography>
      <Box sx={{ width: '100%', bgcolor: 'grey.200', borderRadius: 1, mt: 1, height: 20 }}>
        <Box sx={{ width: '60%', bgcolor: 'primary.main', borderRadius: 1, height: '100%' }} />
      </Box>
    </Card>
  ),

  gauge: (p) => (
    <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-gauge">
      <Typography variant="subtitle2">{p.title}</Typography>
      <Typography variant="caption">[Gauge visualization]</Typography>
    </Card>
  ),

  area_chart: (p) => {
    const rawData = p.data as Record<string, unknown>[] | undefined
    const chartData = rawData && !isPrimitiveArray(rawData) ? rawData : []
    const { xLabels, seriesData } = extractChartData(chartData)
    return (
      <Card variant="outlined" sx={{ p: 2 }} data-testid="a2ui-area_chart">
        {p.title && <Typography variant="subtitle2" gutterBottom>{p.title}</Typography>}
        {xLabels.length > 0 && seriesData.length > 0 ? (
          <LineChart
            xAxis={[{ scaleType: 'point', data: xLabels }]}
            series={[{ data: seriesData, area: true }]}
            height={300}
          />
        ) : (
          <Typography variant="caption" color="text.secondary">
            [No chart data available]
          </Typography>
        )}
      </Card>
    )
  },

  confirmation_card: (p) => (
    <Card variant="outlined" sx={{ p: 2, borderColor: 'warning.main' }} data-testid="a2ui-confirmation_card">
      <Alert severity="warning" sx={{ mb: 2 }}>Action Requires Confirmation</Alert>
      <Typography variant="subtitle2" gutterBottom>
        {p.action?.type === 'create' ? 'CREATE' : p.action?.type === 'update' ? 'UPDATE' : 'DELETE'}
        {' — '}{p.action?.entity}
      </Typography>
      {p.action?.newValues && (
        <Box sx={{ mb: 2 }}>
          {Object.entries(p.action.newValues).map(([key, value]) => (
            <Chip key={key} label={`${key}: ${value}`} size="small" sx={{ m: 0.3 }} />
          ))}
        </Box>
      )}
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button variant="contained" color="success" data-testid="btn-approve">✓ Approve</Button>
        <Button variant="outlined" color="error" data-testid="btn-cancel">✗ Cancel</Button>
      </Box>
    </Card>
  ),

  error_card: (p) => (
    <Alert severity="error" data-testid="a2ui-error_card">
      <Typography variant="subtitle2">{p.title ?? 'Error'}</Typography>
      <Typography variant="body2">{p.description}</Typography>
    </Alert>
  ),

  clarification_prompt: (p) => (
    <Card variant="outlined" sx={{ p: 2, bgcolor: 'grey.50' }} data-testid="a2ui-clarification_prompt">
      <Typography variant="body2" sx={{ mb: 1 }}>{p.title ?? 'Clarification needed'}</Typography>
      {Array.isArray(p.data) && (p.data as string[]).map((opt, i) => (
        <Chip key={i} label={opt} sx={{ m: 0.3 }} onClick={() => {}} />
      ))}
    </Card>
  ),

  proactive_insight: (p) => (
    <Alert severity="info" sx={{ cursor: 'pointer' }} data-testid="a2ui-proactive_insight">
      <Typography variant="subtitle2">{p.title}</Typography>
      <Typography variant="body2">{p.description}</Typography>
    </Alert>
  ),
}

export function A2UIRenderer({ payload }: { payload: A2UIPayload | A2UICompositePayload }) {
  // Composite dashboard
  if (payload.componentType === 'dashboard') {
    const dashboard = payload as A2UICompositePayload
    const layoutStyles = {
      vertical: { display: 'flex', flexDirection: 'column' as const, gap: 2 },
      grid: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 },
      sidebar: { display: 'flex', flexDirection: 'row' as const, gap: 2 },
    }

    return (
      <Box sx={layoutStyles[dashboard.layout] ?? layoutStyles.vertical} data-testid="a2ui-dashboard">
        {dashboard.components.map((child, index) => (
          <div key={index}><A2UIRenderer payload={child} /></div>
        ))}
      </Box>
    )
  }

  // Single component
  const Component = componentRegistry[payload.componentType]

  if (!Component) {
    return (
      <Alert severity="warning">
        Unknown component type: {payload.componentType}
      </Alert>
    )
  }

  return <>{Component(payload)}</>
}
