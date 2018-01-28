/**
 * Handles alert list and filter settings
 * @module manage/alerts/controller
 * @exports alerts controller
 */
import Ember from 'ember';
import { task, timeout } from 'ember-concurrency';
import fetch from 'fetch';
import _ from 'lodash';

export default Ember.Controller.extend({
  queryParams: ['selectedSearchMode', 'alertId'],
  /**
   * Alerts Search Mode options
   */
  searchModes: ['All Alerts', 'Alerts', 'Subscriber Groups', 'Applications'],

  /**
   * Alerts Search Mode options
   */
  sortModes: ['Edited:first', 'Edited:last', 'A to Z', 'Z to A'],

  /**
   * True when results appear
   */
  resultsActive: false,

  /**
   * Default Search Mode
   */
  selectedSearchMode: 'All Alerts',

  /**
   * Default Sort Mode
   */
  selectedSortMode: 'Edited:last',

  /**
   * Array of Alerts we're displaying
   */
  selectedAlerts: [],
  selectedAll: [],
  selectedsubscriberGroupName: [],
  selectedApplicationName: [],

  // default current Page
  currentPage: 1,

  // Alerts to display per PAge
  pageSize: 10,

  // Number of pages to display
  paginationSize: Ember.computed(
    'pagesNum',
    'pageSize',
    function() {
      const pagesNum = this.get('pagesNum');
      const pageSize = this.get('pageSize');

      return Math.min(pagesNum, pageSize/2);
    }
  ),

  // Total Number of pages to display
  pagesNum: Ember.computed(
    'selectedAlerts.length',
    'pageSize',
    function() {
      const numAlerts = this.get('selectedAlerts').length;
      const pageSize = this.get('pageSize');
      return Math.ceil(numAlerts/pageSize);
    }
  ),

  // Groups array for search filter 'search by Subscriber Groups'
  subscriberGroupNames: Ember.computed('model.subscriberGroups', function() {
    const groupNames = this.get('model.subscriberGroups');

    return groupNames
      .filterBy('name')
      .map(group => group.name)
      .uniq()
      .sort();
  }),

  // App names array for search filter 'search by Application'
  applicationNames: Ember.computed('model.applications', function() {
    const appNames = this.get('model.applications');

    return appNames
      .map(app => app.application)
      .sort();
  }),

  // creates the page Array for view
  viewPages: Ember.computed(
    'pages',
    'currentPage',
    'paginationSize',
    'pageNums',
    function() {
      const size = this.get('paginationSize');
      const currentPage = this.get('currentPage');
      const max = this.get('pagesNum');
      const step = Math.floor(size / 2);

      if (max === 1) { return; }

      const startingNumber = ((max - currentPage) < step)
        ? Math.max(max - size + 1, 1)
        : Math.max(currentPage - step, 1);

      return [...new Array(size)].map((page, index) =>  startingNumber + index);
    }
  ),

  // alerts with pagination
  paginatedSelectedAlerts: Ember.computed(
    'selectedAlerts.@each',
    'pageSize',
    'currentPage',
    'selectedSortMode',
    function() {
      const pageSize = this.get('pageSize');
      const pageNumber = this.get('currentPage');
      const sortOrder = this.get('selectedSortMode');
      const allGroups = this.get('model.subscriberGroups');
      let alerts = this.get('selectedAlerts');
      let groupFunctionIds = [];
      let foundAlert = {};

      // Handle selected sort order
      switch(sortOrder) {
        case 'Edited:first': {
          alerts = alerts.sortBy('id');
          break;
        }
        case 'Edited:last': {
          alerts = alerts = alerts.sortBy('id').reverse();
          break;
        }
        case 'A to Z': {
          alerts = alerts.sortBy('functionName');
          break;
        }
        case 'Z to A': {
          alerts = alerts.sortBy('functionName').reverse();
          break;
        }
      }

    // Itereate through config groups to enhance all alerts with extra properties (group name, application)
    for (let config of allGroups) {
      groupFunctionIds = config.emailConfig && config.emailConfig.functionIds ? config.emailConfig.functionIds : [];
      for (let id of groupFunctionIds) {
        foundAlert = _.find(alerts, function(alert) {
          return alert.id === id;
        });
        if (foundAlert) {
          Ember.set(foundAlert, 'application', config.application);
          Ember.set(foundAlert, 'group', config.name);
        }
      }
    }

      return alerts.slice((pageNumber - 1) * pageSize, pageNumber * pageSize);
    }
  ),

  /**
   * Handler for search by function name
   * Utilizing ember concurrency (task)
   */
  searchByFunctionName: task(function* (alert) {
    yield timeout(600);

    const url = `/data/autocomplete/functionByName?name=${alert}`;
    return fetch(url)
      .then(res => res.json());
  }),

  /**
   * Handler for search by application name
   * Utilizing ember concurrency (task)
   */
  searchByApplicationName: task(function* (appName) {
    this.set('isLoading', true);
    yield timeout(600);
    const url = `/data/autocomplete/functionByAppname?appname=${appName}`;

    this.set('selectedApplicationName', appName);
    this.set('currentPage', 1);

    return fetch(url)
      .then(res => res.json())
      .then((alerts) => {
        this.set('isLoading', false);
        this.set('selectedAlerts', alerts);
      });
  }),

  /**
   * Handler for search by subscriber gropu name
   * Utilizing ember concurrency (task)
   */
  searchByDatasetName: task(function* (groupName) {
    this.set('isLoading', true);
    yield timeout(600);

    this.set('selectedsubscriberGroupNames', groupName);
    this.set('currentPage', 1);

    const url = `/data/autocomplete/functionByAlertName?alertName=${groupName}`;
    return fetch(url)
      .then(res => res.json())
      .then((filters) => {
        this.set('isLoading', false);
        this.set('selectedAlerts', filters);
      });
  }),

  actions: {
    // Handles alert selection from type ahead
    onAlertChange(alert) {
      if (!alert) { return; }
      this.set('selectedAlerts', [alert]);
      this.set('primaryMetric', alert);
      this.set('resultsActive', true);
    },

    // Handles filtering of alerts in response to filter selection
    userDidSelectFilter(filterArr) {
      let task = {};

      // Reset results
      this.send('onSearchModeChange', 'All Alerts');

      // Now filter results accordingly. TODO: build filtering for each type.
      for (let filter of filterArr) {
        switch(filter.category) {
          case 'Applications': {
            this.send('onSearchModeChange', 'Applications');
            let task = this.get('searchByApplicationName');
            let taskInstance = task.perform(filter.filter);
            return taskInstance;
          }
        }
      }
    },

    // Handles UI mode change
    onSearchModeChange(mode) {
      if (mode === 'All Alerts') {
        const allAlerts = this.get('model.alerts');
        this.setProperties({
          selectedAlerts: allAlerts,
          resultsActive: true
        });
      }
      this.set('selectedSearchMode', mode);
    },

    // Handles UI sort change
    onSortModeChange(mode) {
      this.set('selectedSortMode', mode);
    },

    /**
     * action handler for page clicks
     * @param {Number|String} page
     */
    onPaginationClick(page) {
      let newPage = page;
      let currentPage = this.get('currentPage');

      switch (page) {
        case 'previous':
          newPage = --currentPage;
          break;
        case 'next':
          newPage = ++currentPage;
          break;
      }

      this.set('currentPage', newPage);
    }
  }
});
