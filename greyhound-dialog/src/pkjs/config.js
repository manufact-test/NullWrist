module.exports = [
  {type:'heading', defaultValue:'Greyhound Dialog'},
  {type:'section', items:[
    {type:'heading', defaultValue:'Палитра'},
    {type:'color', messageKey:'BackgroundColor', label:'Фон', defaultValue:'0x000000', allowGray:true},
    {type:'color', messageKey:'BubbleColor', label:'Окно', defaultValue:'0xFFFFFF', allowGray:true},
    {type:'color', messageKey:'BorderColor', label:'Рамка', defaultValue:'0x000000', allowGray:true},
    {type:'color', messageKey:'TextColor', label:'Текст', defaultValue:'0x000000', allowGray:true},
    {type:'color', messageKey:'AccentColor', label:'Акцент / ошейник', defaultValue:'0xFF5500', allowGray:true}
  ]},
  {type:'section', items:[
    {type:'heading', defaultValue:'Время и погода'},
    {type:'toggle', messageKey:'Use24h', label:'24-часовой формат', defaultValue:true},
    {type:'toggle', messageKey:'AutoLocation', label:'Автоопределение локации', defaultValue:true},
    {type:'input', messageKey:'ManualLocation', label:'Локация вручную', defaultValue:'', attributes:{placeholder:'Warsaw, Poland'}}
  ]},
  {type:'section', items:[
    {type:'heading', defaultValue:'Автор'},
    {type:'text', defaultValue:'Если пёс оказался полезным — можно угостить автора кофе.'},
    {type:'button', defaultValue:'☕ Поддержать автора', id:'donate', attributes:{onclick:"window.location='https://wise.com/pay/me/ilyas709';"}}
  ]},
  {type:'submit', defaultValue:'Сохранить'}
];
